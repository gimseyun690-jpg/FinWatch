package com.finwatch.realtime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.finwatch.data.provider.KisMarketDataClient;
import com.finwatch.data.provider.ProviderRestClientFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * KIS overseas stock realtime WebSocket client using TR_ID: HDFSCNT0.
 * <p>
 * Subscribes to US stock real-time trades via KIS WebSocket using exchange-prefixed
 * subscription keys (e.g. "NASAAPL" for NASDAQ AAPL, "NYSTM" for NYSE TM).
 * <p>
 * This provides real-time prices during pre-market (04:00-09:30 ET)
 * and after-hours (16:00-20:00 ET) in addition to regular trading hours.
 */
@Component
public class KisOverseasRealtimeClient {

    private static final String PROVIDER = "KIS_OVERSEAS";
    private static final String TR_ID = "HDFSCNT0";
    private static final int MAX_SUBSCRIPTIONS = 41;

    private final String appKey;
    private final String appSecret;
    private final URI websocketUri;
    private final Duration connectTimeout;
    private final Duration maxReconnectDelay;
    private final Duration staleTimeout;
    private final Duration staleCheckInterval;
    private final RestClient approvalClient;
    private final HttpClient websocketClient;
    private final ObjectMapper objectMapper;
    private final KisMarketDataClient marketDataClient;
    private final RealtimeQuoteHub hub;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicBoolean connectionPending = new AtomicBoolean();
    private final AtomicBoolean watchdogStarted = new AtomicBoolean();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();

    private volatile List<String> symbols = List.of();
    private volatile Map<String, String> symbolMarkets = Map.of(); // symbol -> market (NASDAQ/NYSE)
    private volatile WebSocket activeSocket;
    private volatile boolean stopped = true;
    private volatile String cachedApprovalKey;
    private volatile Instant approvalKeyExpiresAt = Instant.EPOCH;
    private volatile Instant lastInboundAt = Instant.EPOCH;
    private volatile Instant lastTickAt = Instant.EPOCH;
    private volatile boolean streamConfirmed;

    public KisOverseasRealtimeClient(
            @Value("${app.data.kis.app-key:}") String appKey,
            @Value("${app.data.kis.app-secret:}") String appSecret,
            @Value("${app.data.kis.environment:paper}") String environment,
            @Value("${app.data.kis.prod-base-url}") String prodBaseUrl,
            @Value("${app.data.kis.paper-base-url}") String paperBaseUrl,
            @Value("${app.data.kis.prod-websocket-url}") String prodWebsocketUrl,
            @Value("${app.data.kis.paper-websocket-url}") String paperWebsocketUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout,
            @Value("${app.realtime.reconnect-max-delay:30s}") Duration maxReconnectDelay,
            @Value("${app.realtime.kis-overseas-stale-timeout:120s}") Duration staleTimeout,
            @Value("${app.realtime.kis-overseas-stale-check-interval:20s}") Duration staleCheckInterval,
            ObjectMapper objectMapper,
            KisMarketDataClient marketDataClient,
            RealtimeQuoteHub hub) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.connectTimeout = connectTimeout;
        this.maxReconnectDelay = maxReconnectDelay;
        this.staleTimeout = positiveOrDefault(staleTimeout, Duration.ofSeconds(120));
        this.staleCheckInterval = positiveOrDefault(staleCheckInterval, Duration.ofSeconds(20));
        boolean prod = "prod".equalsIgnoreCase(environment);
        this.websocketUri = URI.create((prod ? prodWebsocketUrl : paperWebsocketUrl) + "/tryitout");
        this.approvalClient = ProviderRestClientFactory.create(
                prod ? prodBaseUrl : paperBaseUrl,
                connectTimeout,
                readTimeout);
        this.websocketClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.objectMapper = objectMapper;
        this.marketDataClient = marketDataClient;
        this.hub = hub;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kis-overseas-realtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start(List<String> symbols, Map<String, String> marketsBySymbol) {
        this.symbols = normalize(symbols);
        this.symbolMarkets = Map.copyOf(marketsBySymbol);
        stopped = false;
        if (appKey.isBlank() || appSecret.isBlank()) {
            hub.updateProvider(PROVIDER, "ERROR", "KIS_APP_KEY와 KIS_APP_SECRET이 필요합니다.");
            return;
        }
        startWatchdog();
        scheduler.execute(() -> {
            seedSnapshots();
            connect();
        });
    }

    public void updateSubscriptions(List<String> desiredSymbols, Map<String, String> marketsBySymbol) {
        List<String> desired = normalize(desiredSymbols);
        List<String> previous = symbols;
        symbols = desired;
        symbolMarkets = Map.copyOf(marketsBySymbol);
        if (stopped) {
            return;
        }
        if (appKey.isBlank() || appSecret.isBlank()) {
            hub.updateProvider(PROVIDER, "ERROR", "KIS_APP_KEY와 KIS_APP_SECRET이 필요합니다.");
            return;
        }
        Set<String> previousSet = Set.copyOf(previous);
        Set<String> desiredSet = Set.copyOf(desired);
        List<String> additions = desired.stream().filter(s -> !previousSet.contains(s)).toList();
        List<String> removals = previous.stream().filter(s -> !desiredSet.contains(s)).toList();
        WebSocket socket = activeSocket;
        if (socket == null) {
            scheduler.execute(() -> {
                seedSnapshots(additions);
                connect();
            });
            return;
        }
        String approvalKey = cachedApprovalKey;
        if (approvalKey == null || approvalKey.isBlank()) {
            return;
        }
        removals.forEach(symbol -> socket.sendText(
                subscriptionMessage(approvalKey, symbol, "0"), true));
        seedSnapshots(additions);
        additions.forEach(symbol -> socket.sendText(
                subscriptionMessage(approvalKey, symbol, "1"), true));
        if (desired.isEmpty()) {
            streamConfirmed = false;
            hub.updateProvider(PROVIDER, "IDLE", connectionMessage(0));
        }
    }

    public List<String> subscribedSymbols() {
        return symbols;
    }

    public void stop() {
        stopped = true;
        WebSocket socket = activeSocket;
        activeSocket = null;
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
        scheduler.shutdownNow();
        hub.updateProvider(PROVIDER, "DISCONNECTED", "해외주식 실시간 구독을 종료했습니다.");
    }

    // ---- Seed initial REST snapshots ----

    private void seedSnapshots() {
        seedSnapshots(symbols);
    }

    private void seedSnapshots(List<String> targetSymbols) {
        for (String symbol : targetSymbols) {
            if (stopped) {
                return;
            }
            try {
                String market = marketFor(symbol);
                var quote = marketDataClient.getOverseasQuote(market, symbol);
                hub.publish(new LiveQuote(
                        market,
                        quote.symbol(),
                        quote.price(),
                        quote.change(),
                        quote.changeRate(),
                        quote.volume(),
                        quote.currency(),
                        quote.fetchedAt(),
                        "KIS_OVERSEAS_REST",
                        "SNAPSHOT"));
            } catch (RuntimeException exception) {
                hub.updateProvider(PROVIDER, "DEGRADED",
                        "해외주식 초기 현재가 조회 실패 (" + symbol + "): " + safeMessage(exception));
            }
        }
    }

    // ---- WebSocket connection management ----

    private void connect() {
        if (stopped || activeSocket != null || !connectionPending.compareAndSet(false, true)) {
            return;
        }
        try {
            hub.updateProvider(
                    PROVIDER,
                    reconnectAttempts.get() == 0 ? "CONNECTING" : "RECONNECTING",
                    "KIS 해외주식 체결 스트림 연결 중");
            String approvalKey = approvalKey();
            websocketClient.newWebSocketBuilder()
                    .connectTimeout(connectTimeout)
                    .buildAsync(websocketUri, new Listener(approvalKey))
                    .whenComplete((socket, error) -> {
                        if (error != null) {
                            connectionPending.set(false);
                            hub.updateProvider(PROVIDER, "ERROR",
                                    "KIS 해외 WebSocket 연결 실패: " + safeMessage(error));
                            scheduleReconnect();
                        }
                    });
        } catch (RuntimeException exception) {
            connectionPending.set(false);
            hub.updateProvider(PROVIDER, "ERROR",
                    "KIS 해외 실시간 인증 실패: " + safeMessage(exception));
            scheduleReconnect();
        }
    }

    private String approvalKey() {
        if (cachedApprovalKey != null && approvalKeyExpiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return cachedApprovalKey;
        }
        if (appKey.isBlank() || appSecret.isBlank()) {
            throw new IllegalStateException("KIS_APP_KEY와 KIS_APP_SECRET이 필요합니다.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> response = approvalClient.post()
                .uri("/oauth2/Approval")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", appKey,
                        "secretkey", appSecret))
                .retrieve()
                .body(Map.class);
        String key = response == null ? "" : String.valueOf(response.getOrDefault("approval_key", ""));
        if (key.isBlank()) {
            throw new IllegalStateException("KIS approval_key가 응답에 없습니다.");
        }
        cachedApprovalKey = key;
        approvalKeyExpiresAt = Instant.now().plus(Duration.ofHours(23));
        return key;
    }

    private void scheduleReconnect() {
        scheduleReconnect(null);
    }

    private void scheduleReconnect(String reason) {
        if (stopped || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        int attempt = reconnectAttempts.incrementAndGet();
        long maxSeconds = Math.max(1, maxReconnectDelay.toSeconds());
        long delay = Math.min(maxSeconds, 1L << Math.min(attempt - 1, 5));
        String message = reason == null || reason.isBlank()
                ? delay + "초 후 해외주식 다시 연결합니다."
                : reason + " · " + delay + "초 후 해외주식 다시 연결합니다.";
        hub.updateProvider(PROVIDER, "RECONNECTING", message);
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connect();
        }, delay, TimeUnit.SECONDS);
    }

    // ---- Subscription message ----

    private String subscriptionMessage(String approvalKey, String symbol, String type) {
        String trKey = exchangePrefix(symbol) + symbol;
        return objectMapper.writeValueAsString(Map.of(
                "header", Map.of(
                        "approval_key", approvalKey,
                        "custtype", "P",
                        "tr_type", type,
                        "content-type", "utf-8"),
                "body", Map.of("input", Map.of(
                        "tr_id", TR_ID,
                        "tr_key", trKey))));
    }

    private String subscriptionMessage(String approvalKey, String symbol) {
        return subscriptionMessage(approvalKey, symbol, "1");
    }

    /**
     * Returns the KIS exchange prefix for a symbol: NAS, NYS, or AMS.
     */
    private String exchangePrefix(String symbol) {
        String market = marketFor(symbol);
        return switch (market) {
            case "NYSE" -> "NYS";
            case "AMEX", "ARCA" -> "AMS";
            default -> "NAS"; // NASDAQ default
        };
    }

    private String marketFor(String symbol) {
        String market = symbolMarkets.get(symbol);
        if (market != null) {
            return market.toUpperCase(Locale.ROOT);
        }
        return "NASDAQ";
    }

    // ---- Message handling ----

    void handleMessage(WebSocket socket, String message, Instant receivedAt) {
        if (activeSocket != socket) {
            return;
        }
        recordInbound(socket, receivedAt);
        if (message.startsWith("0|")) {
            List<LiveQuote> quotes = KisOverseasTradeMessageParser.parse(message);
            for (LiveQuote quote : quotes) {
                hub.publish(quote);
            }
            if (!quotes.isEmpty()) {
                lastTickAt = receivedAt;
                confirmStream(socket);
            }
            return;
        }
        if (!message.startsWith("{")) {
            return;
        }
        try {
            var root = objectMapper.readTree(message);
            if ("PINGPONG".equals(root.path("header").path("tr_id").asText())) {
                byte[] payload = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (payload.length <= 125) {
                    socket.sendPong(ByteBuffer.wrap(payload));
                } else {
                    socket.sendText(message, true);
                }
                return;
            }
            String resultCode = root.path("body").path("rt_cd").asText("0");
            if (!"0".equals(resultCode)) {
                streamConfirmed = false;
                hub.updateProvider(PROVIDER, "DEGRADED",
                        root.path("body").path("msg1").asText("KIS 해외 구독 오류"));
                return;
            }
            String transactionId = root.path("header").path("tr_id").asText();
            if (TR_ID.equals(transactionId)) {
                confirmStream(socket);
            }
        } catch (RuntimeException ignored) {
            // Unknown provider control messages are ignored.
        }
    }

    // ---- Lifecycle helpers ----

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .limit(MAX_SUBSCRIPTIONS)
                .toList()));
    }

    private void startWatchdog() {
        if (!watchdogStarted.compareAndSet(false, true)) {
            return;
        }
        long intervalMillis = Math.max(1_000, staleCheckInterval.toMillis());
        scheduler.scheduleWithFixedDelay(
                this::safeCheckLiveness,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS);
    }

    private void safeCheckLiveness() {
        try {
            checkLiveness(Instant.now());
        } catch (RuntimeException exception) {
            hub.updateProvider(PROVIDER, "DEGRADED",
                    "KIS 해외 실시간 상태 확인 실패: " + safeMessage(exception));
        }
    }

    void checkLiveness(Instant now) {
        WebSocket socket = activeSocket;
        Instant lastInbound = lastInboundAt;
        if (socket == null || symbols.isEmpty() || lastInbound.equals(Instant.EPOCH)) {
            return;
        }
        Duration silence = Duration.between(lastInbound, now);
        if (silence.isNegative() || silence.compareTo(staleTimeout) <= 0 || activeSocket != socket) {
            return;
        }
        activeSocket = null;
        connectionPending.set(false);
        streamConfirmed = false;
        String reason = "KIS 해외 실시간 수신이 " + Math.max(1, silence.toSeconds()) + "초 동안 없어 연결을 교체합니다.";
        hub.updateProvider(PROVIDER, "DEGRADED", reason);
        socket.abort();
        scheduleReconnect(reason);
    }

    private void registerOpenSocket(WebSocket socket, Instant openedAt) {
        activeSocket = socket;
        lastInboundAt = openedAt;
        lastTickAt = Instant.EPOCH;
        streamConfirmed = false;
    }

    private void recordInbound(WebSocket socket, Instant receivedAt) {
        if (activeSocket == socket) {
            lastInboundAt = receivedAt;
        }
    }

    private void confirmStream(WebSocket socket) {
        if (activeSocket != socket || streamConfirmed) {
            return;
        }
        streamConfirmed = true;
        reconnectAttempts.set(0);
        reconnectScheduled.set(false);
        hub.updateProvider(PROVIDER, "CONNECTED", connectionMessage(symbols.size()));
    }

    void handleSocketOpen(WebSocket webSocket, String approvalKey, Instant openedAt) {
        connectionPending.set(false);
        registerOpenSocket(webSocket, openedAt);
        webSocket.request(1);
        hub.updateProvider(
                PROVIDER,
                symbols.isEmpty() ? "IDLE" : "SUBSCRIBING",
                symbols.isEmpty() ? connectionMessage(0) : "KIS 해외주식 구독 승인을 기다리는 중");
        CompletableFuture<?> subscriptions = CompletableFuture.completedFuture(null);
        for (String symbol : symbols) {
            subscriptions = subscriptions.thenCompose(ignored -> webSocket.sendText(
                    subscriptionMessage(approvalKey, symbol),
                    true));
        }
        subscriptions.whenComplete((ignored, error) -> {
            if (error != null && activeSocket == webSocket) {
                hub.updateProvider(PROVIDER, "ERROR", "KIS 해외 구독 요청 실패: " + safeMessage(error));
                activeSocket = null;
                streamConfirmed = false;
                webSocket.abort();
                scheduleReconnect();
            }
        });
    }

    private String connectionMessage(int subscriptionCount) {
        return subscriptionCount == 0
                ? "KIS 해외주식 연결됨 · 구독 종목 없음"
                : subscriptionCount + "개 해외주식 종목 실시간 체결 구독 중";
    }

    private String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    // ---- WebSocket Listener ----

    private final class Listener implements WebSocket.Listener {

        private final String approvalKey;
        private final StringBuilder buffer = new StringBuilder();

        private Listener(String approvalKey) {
            this.approvalKey = approvalKey;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            if (stopped) {
                connectionPending.set(false);
                webSocket.abort();
                return;
            }
            handleSocketOpen(webSocket, approvalKey, Instant.now());
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(webSocket, message, Instant.now());
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (activeSocket == webSocket && !stopped) {
                connectionPending.set(false);
                activeSocket = null;
                streamConfirmed = false;
                hub.updateProvider(PROVIDER, "DISCONNECTED", "KIS 해외 연결 종료: " + statusCode);
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (activeSocket == webSocket && !stopped) {
                connectionPending.set(false);
                activeSocket = null;
                streamConfirmed = false;
                hub.updateProvider(PROVIDER, "ERROR", "KIS 해외 스트림 오류: " + safeMessage(error));
                scheduleReconnect();
            }
        }
    }
}
