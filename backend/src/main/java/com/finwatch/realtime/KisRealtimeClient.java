package com.finwatch.realtime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
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
import com.finwatch.data.provider.KisDomesticMarket;
import com.finwatch.data.provider.ProviderRestClientFactory;

import tools.jackson.databind.ObjectMapper;

@Component
public class KisRealtimeClient {

    private static final String PROVIDER = "KIS";

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
    private final KisDomesticMarket domesticMarket;
    private final RealtimeQuoteHub hub;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicBoolean connectionPending = new AtomicBoolean();
    private final AtomicBoolean watchdogStarted = new AtomicBoolean();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();

    private volatile List<String> symbols = List.of();
    private volatile WebSocket activeSocket;
    private volatile boolean stopped = true;
    private volatile String cachedApprovalKey;
    private volatile Instant approvalKeyExpiresAt = Instant.EPOCH;
    private volatile Instant lastInboundAt = Instant.EPOCH;
    private volatile Instant lastTickAt = Instant.EPOCH;
    private volatile boolean streamConfirmed;

    public KisRealtimeClient(
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
            @Value("${app.realtime.kis-stale-timeout:90s}") Duration staleTimeout,
            @Value("${app.realtime.kis-stale-check-interval:15s}") Duration staleCheckInterval,
            ObjectMapper objectMapper,
            KisMarketDataClient marketDataClient,
            RealtimeQuoteHub hub) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.connectTimeout = connectTimeout;
        this.maxReconnectDelay = maxReconnectDelay;
        this.staleTimeout = positiveOrDefault(staleTimeout, Duration.ofSeconds(90));
        this.staleCheckInterval = positiveOrDefault(staleCheckInterval, Duration.ofSeconds(15));
        boolean prod = "prod".equalsIgnoreCase(environment);
        this.websocketUri = URI.create((prod ? prodWebsocketUrl : paperWebsocketUrl) + "/tryitout");
        this.approvalClient = ProviderRestClientFactory.create(
                prod ? prodBaseUrl : paperBaseUrl,
                connectTimeout,
                readTimeout);
        this.websocketClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.objectMapper = objectMapper;
        this.marketDataClient = marketDataClient;
        this.domesticMarket = marketDataClient.domesticMarket() == null
                ? KisDomesticMarket.KRX
                : marketDataClient.domesticMarket();
        this.hub = hub;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kis-realtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start(List<String> symbols) {
        this.symbols = normalize(symbols);
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

    public void updateSubscriptions(List<String> desiredSymbols) {
        List<String> desired = normalize(desiredSymbols);
        List<String> previous = symbols;
        symbols = desired;
        if (stopped) {
            return;
        }
        if (appKey.isBlank() || appSecret.isBlank()) {
            hub.updateProvider(PROVIDER, "ERROR", "KIS_APP_KEY와 KIS_APP_SECRET이 필요합니다.");
            return;
        }
        Set<String> previousSet = Set.copyOf(previous);
        Set<String> desiredSet = Set.copyOf(desired);
        List<String> additions = desired.stream().filter(symbol -> !previousSet.contains(symbol)).toList();
        List<String> removals = previous.stream().filter(symbol -> !desiredSet.contains(symbol)).toList();
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
        removals.forEach(symbol -> socket.sendText(subscriptionMessage(approvalKey, symbol, "0"), true));
        seedSnapshots(additions);
        additions.forEach(symbol -> socket.sendText(subscriptionMessage(approvalKey, symbol, "1"), true));
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
        hub.updateProvider(PROVIDER, "DISCONNECTED", "실시간 구독을 종료했습니다.");
    }

    private void seedSnapshots() {
        seedSnapshots(symbols);
    }

    private void seedSnapshots(List<String> targetSymbols) {
        for (String symbol : targetSymbols) {
            if (stopped) {
                return;
            }
            try {
                var quote = marketDataClient.getDomesticQuote(symbol);
                hub.publish(new LiveQuote(
                        "KRX",
                        quote.symbol(),
                        quote.price(),
                        quote.change(),
                        quote.changeRate(),
                        quote.volume(),
                        quote.currency(),
                        quote.fetchedAt(),
                        domesticMarket.restSource(),
                        "SNAPSHOT"));
            } catch (RuntimeException exception) {
                hub.updateProvider(PROVIDER, "DEGRADED", "초기 현재가 조회 실패: " + safeMessage(exception));
            }
        }
    }

    private void connect() {
        if (stopped || activeSocket != null || !connectionPending.compareAndSet(false, true)) {
            return;
        }
        try {
            hub.updateProvider(
                    PROVIDER,
                    reconnectAttempts.get() == 0 ? "CONNECTING" : "RECONNECTING",
                    "KIS " + domesticMarket.displayName() + " 체결 스트림 연결 중");
            String approvalKey = approvalKey();
            websocketClient.newWebSocketBuilder()
                    .connectTimeout(connectTimeout)
                    .buildAsync(websocketUri, new Listener(approvalKey))
                    .whenComplete((socket, error) -> {
                        if (error != null) {
                            connectionPending.set(false);
                            hub.updateProvider(PROVIDER, "ERROR", "KIS WebSocket 연결 실패: " + safeMessage(error));
                            scheduleReconnect();
                        }
                    });
        } catch (RuntimeException exception) {
            connectionPending.set(false);
            hub.updateProvider(PROVIDER, "ERROR", "KIS 실시간 인증 실패: " + safeMessage(exception));
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
                ? delay + "초 후 다시 연결합니다."
                : reason + " · " + delay + "초 후 다시 연결합니다.";
        hub.updateProvider(PROVIDER, "RECONNECTING", message);
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connect();
        }, delay, TimeUnit.SECONDS);
    }

    private String subscriptionMessage(String approvalKey, String symbol) {
        return subscriptionMessage(approvalKey, symbol, "1");
    }

    private String subscriptionMessage(String approvalKey, String symbol, String type) {
        return objectMapper.writeValueAsString(Map.of(
                "header", Map.of(
                        "approval_key", approvalKey,
                        "custtype", "P",
                        "tr_type", type,
                        "content-type", "utf-8"),
                "body", Map.of("input", Map.of(
                        "tr_id", domesticMarket.realtimeTrId(),
                        "tr_key", symbol))));
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .limit(40)
                .toList()));
    }

    void handleMessage(WebSocket socket, String message, Instant receivedAt) {
        if (activeSocket != socket) {
            return;
        }
        recordInbound(socket, receivedAt);
        if (message.startsWith("0|")) {
            List<LiveQuote> quotes = KisTradeMessageParser.parse(message);
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
                hub.updateProvider(PROVIDER, "DEGRADED", root.path("body").path("msg1").asText("KIS 구독 오류"));
                return;
            }
            String transactionId = root.path("header").path("tr_id").asText();
            if (domesticMarket.realtimeTrId().equals(transactionId)) {
                confirmStream(socket);
            }
        } catch (RuntimeException ignored) {
            // Unknown provider control messages are ignored without dropping the stream.
        }
    }

    private String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private String connectionMessage(int subscriptionCount) {
        return subscriptionCount == 0
                ? "KIS 연결됨 · 구독 종목 없음"
                : subscriptionCount + "개 " + domesticMarket.displayName() + " 종목 체결 구독 중";
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
            hub.updateProvider(PROVIDER, "DEGRADED", "KIS 실시간 상태 확인 실패: " + safeMessage(exception));
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
        String reason = "KIS 실시간 수신이 " + Math.max(1, silence.toSeconds()) + "초 동안 없어 연결을 교체합니다.";
        hub.updateProvider(PROVIDER, "DEGRADED", reason);
        socket.abort();
        scheduleReconnect(reason);
    }

    void registerOpenSocket(WebSocket socket, Instant openedAt) {
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

    Instant lastInboundAt() {
        return lastInboundAt;
    }

    Instant lastTickAt() {
        return lastTickAt;
    }

    void handleSocketOpen(WebSocket webSocket, String approvalKey, Instant openedAt) {
        connectionPending.set(false);
        registerOpenSocket(webSocket, openedAt);
        webSocket.request(1);
        hub.updateProvider(
                PROVIDER,
                symbols.isEmpty() ? "IDLE" : "SUBSCRIBING",
                symbols.isEmpty() ? connectionMessage(0) : "KIS 구독 승인을 기다리는 중");
        CompletableFuture<?> subscriptions = CompletableFuture.completedFuture(null);
        for (String symbol : symbols) {
            subscriptions = subscriptions.thenCompose(ignored -> webSocket.sendText(
                    subscriptionMessage(approvalKey, symbol),
                    true));
        }
        subscriptions.whenComplete((ignored, error) -> {
            if (error != null && activeSocket == webSocket) {
                hub.updateProvider(PROVIDER, "ERROR", "KIS 구독 요청 실패: " + safeMessage(error));
                activeSocket = null;
                streamConfirmed = false;
                webSocket.abort();
                scheduleReconnect();
            }
        });
    }

    private Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

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
                hub.updateProvider(PROVIDER, "DISCONNECTED", "KIS 연결 종료: " + statusCode);
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
                hub.updateProvider(PROVIDER, "ERROR", "KIS 스트림 오류: " + safeMessage(error));
                scheduleReconnect();
            }
        }
    }
}
