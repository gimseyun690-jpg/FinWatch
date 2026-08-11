package com.finwatch.realtime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import com.finwatch.data.provider.ProviderRestClientFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * KIS HDFSCNT0 client for US stocks. This is intentionally separate from the
 * domestic KIS client: its transaction id, subscription key, and 25-field
 * payload contract are different.
 */
@Component
public class KisOverseasRealtimeClient {

    private static final String PROVIDER = "KIS_OVERSEAS";

    private final String appKey;
    private final String appSecret;
    private final boolean production;
    private final URI websocketUri;
    private final Duration connectTimeout;
    private final Duration maxReconnectDelay;
    private final Duration staleTimeout;
    private final Duration staleCheckInterval;
    private final int maxSubscriptions;
    private final RestClient approvalClient;
    private final HttpClient websocketClient;
    private final ObjectMapper objectMapper;
    private final UsMarketSessionResolver usMarketSessionResolver;
    private final RealtimeQuoteHub hub;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicBoolean connectionPending = new AtomicBoolean();
    private final AtomicBoolean watchdogStarted = new AtomicBoolean();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();

    private volatile List<KisOverseasSubscription> subscriptions = List.of();
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
            @Value("${app.realtime.kis-stale-timeout:90s}") Duration staleTimeout,
            @Value("${app.realtime.kis-stale-check-interval:15s}") Duration staleCheckInterval,
            @Value("${app.realtime.kis-overseas-max-subscriptions:40}") int maxSubscriptions,
            ObjectMapper objectMapper,
            UsMarketSessionResolver usMarketSessionResolver,
            RealtimeQuoteHub hub) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.connectTimeout = positiveOrDefault(connectTimeout, Duration.ofSeconds(3));
        this.maxReconnectDelay = positiveOrDefault(maxReconnectDelay, Duration.ofSeconds(30));
        this.staleTimeout = positiveOrDefault(staleTimeout, Duration.ofSeconds(90));
        this.staleCheckInterval = positiveOrDefault(staleCheckInterval, Duration.ofSeconds(15));
        this.maxSubscriptions = Math.max(1, Math.min(40, maxSubscriptions));
        this.production = "prod".equalsIgnoreCase(environment);
        this.websocketUri = URI.create((production ? prodWebsocketUrl : paperWebsocketUrl) + "/tryitout");
        this.approvalClient = ProviderRestClientFactory.create(
                production ? prodBaseUrl : paperBaseUrl,
                this.connectTimeout,
                readTimeout);
        this.websocketClient = HttpClient.newBuilder().connectTimeout(this.connectTimeout).build();
        this.objectMapper = objectMapper;
        this.usMarketSessionResolver = usMarketSessionResolver;
        this.hub = hub;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kis-overseas-realtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start(List<KisOverseasSubscription> desiredSubscriptions) {
        subscriptions = normalize(desiredSubscriptions);
        stopped = false;
        if (!production) {
            hub.updateProvider(PROVIDER, "DISABLED", "KIS HDFSCNT0 overseas streaming requires KIS_ENV=prod.");
            return;
        }
        if (appKey.isBlank() || appSecret.isBlank()) {
            hub.updateProvider(PROVIDER, "ERROR", "KIS overseas WebSocket requires KIS_APP_KEY and KIS_APP_SECRET.");
            return;
        }
        startWatchdog();
        scheduler.execute(this::connect);
    }

    public void updateSubscriptions(List<KisOverseasSubscription> desiredSubscriptions) {
        List<KisOverseasSubscription> desired = normalize(desiredSubscriptions);
        List<KisOverseasSubscription> previous = subscriptions;
        subscriptions = desired;
        if (stopped) {
            return;
        }
        if (!production) {
            hub.updateProvider(PROVIDER, "DISABLED", "KIS HDFSCNT0 overseas streaming requires KIS_ENV=prod.");
            return;
        }
        if (appKey.isBlank() || appSecret.isBlank()) {
            hub.updateProvider(PROVIDER, "ERROR", "KIS overseas WebSocket requires KIS_APP_KEY and KIS_APP_SECRET.");
            return;
        }

        Map<String, KisOverseasSubscription> previousByKey = byKey(previous);
        Map<String, KisOverseasSubscription> desiredByKey = byKey(desired);
        List<KisOverseasSubscription> removals = previous.stream()
                .filter(subscription -> !desiredByKey.containsKey(subscription.trKey()))
                .toList();
        List<KisOverseasSubscription> additions = desired.stream()
                .filter(subscription -> !previousByKey.containsKey(subscription.trKey()))
                .toList();
        WebSocket socket = activeSocket;
        String approvalKey = cachedApprovalKey;
        if (socket == null || approvalKey == null || approvalKey.isBlank()) {
            scheduler.execute(this::connect);
            return;
        }
        removals.forEach(subscription -> socket.sendText(subscriptionMessage(approvalKey, subscription, "0"), true));
        additions.forEach(subscription -> socket.sendText(subscriptionMessage(approvalKey, subscription, "1"), true));
        if (desired.isEmpty()) {
            streamConfirmed = false;
            hub.updateProvider(PROVIDER, "IDLE", connectionMessage(0));
        }
    }

    public List<KisOverseasSubscription> subscriptions() {
        return subscriptions;
    }

    public void stop() {
        stopped = true;
        WebSocket socket = activeSocket;
        activeSocket = null;
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
        scheduler.shutdownNow();
        hub.updateProvider(PROVIDER, "DISCONNECTED", "KIS overseas real-time subscription stopped.");
    }

    void handleMessage(WebSocket socket, String message, Instant receivedAt) {
        if (activeSocket != socket) {
            return;
        }
        lastInboundAt = receivedAt;
        if (message.startsWith("0|")) {
            List<KisOverseasTradeMessageParser.KisOverseasTrade> trades =
                    KisOverseasTradeMessageParser.parse(message, receivedAt);
            for (KisOverseasTradeMessageParser.KisOverseasTrade trade : trades) {
                if (!trade.providerTimestamp()) {
                    continue;
                }
                KisOverseasSubscription subscription = subscriptionFor(trade.providerSymbol());
                if (subscription == null) {
                    continue;
                }
                String sessionStatus = sessionStatus(subscription, trade.asOf());
                if (!MarketSessionStatus.isStreaming(sessionStatus)) {
                    continue;
                }
                hub.publish(new LiveQuote(
                        subscription.market(),
                        subscription.symbol(),
                        trade.price(),
                        trade.change(),
                        trade.changeRate(),
                        trade.volume(),
                        "USD",
                        trade.asOf(),
                        "KIS_OVERSEAS_WS",
                        sessionStatus));
            }
            if (!trades.isEmpty()) {
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
                        root.path("body").path("msg1").asText("KIS overseas subscription rejected."));
                return;
            }
            if (KisOverseasTradeMessageParser.TRANSACTION_ID.equals(root.path("header").path("tr_id").asText())) {
                confirmStream(socket);
            }
        } catch (RuntimeException ignored) {
            // Unknown provider control messages do not invalidate the active connection.
        }
    }

    void handleSocketOpen(WebSocket webSocket, String approvalKey, Instant openedAt) {
        connectionPending.set(false);
        activeSocket = webSocket;
        lastInboundAt = openedAt;
        lastTickAt = Instant.EPOCH;
        streamConfirmed = false;
        webSocket.request(1);
        hub.updateProvider(
                PROVIDER,
                subscriptions.isEmpty() ? "IDLE" : "SUBSCRIBING",
                subscriptions.isEmpty() ? connectionMessage(0) : "Awaiting KIS overseas subscription acknowledgement.");
        CompletableFuture<?> writes = CompletableFuture.completedFuture(null);
        for (KisOverseasSubscription subscription : subscriptions) {
            writes = writes.thenCompose(ignored -> webSocket.sendText(
                    subscriptionMessage(approvalKey, subscription, "1"), true));
        }
        writes.whenComplete((ignored, error) -> {
            if (error != null && activeSocket == webSocket) {
                activeSocket = null;
                streamConfirmed = false;
                hub.updateProvider(PROVIDER, "ERROR", "KIS overseas subscription request failed: " + safeMessage(error));
                webSocket.abort();
                scheduleReconnect();
            }
        });
    }

    void checkLiveness(Instant now) {
        WebSocket socket = activeSocket;
        if (socket == null || subscriptions.isEmpty() || lastInboundAt.equals(Instant.EPOCH)) {
            return;
        }
        Duration silence = Duration.between(lastInboundAt, now);
        if (silence.isNegative() || silence.compareTo(staleTimeout) <= 0 || activeSocket != socket) {
            return;
        }
        activeSocket = null;
        connectionPending.set(false);
        streamConfirmed = false;
        String reason = "KIS overseas stream has been silent for " + Math.max(1, silence.toSeconds()) + " seconds.";
        hub.updateProvider(PROVIDER, "DEGRADED", reason);
        socket.abort();
        scheduleReconnect(reason);
    }

    Instant lastTickAt() {
        return lastTickAt;
    }

    private void connect() {
        if (stopped || activeSocket != null || !connectionPending.compareAndSet(false, true)) {
            return;
        }
        try {
            hub.updateProvider(
                    PROVIDER,
                    reconnectAttempts.get() == 0 ? "CONNECTING" : "RECONNECTING",
                    "Connecting KIS HDFSCNT0 overseas trade stream.");
            String approvalKey = approvalKey();
            websocketClient.newWebSocketBuilder()
                    .connectTimeout(connectTimeout)
                    .buildAsync(websocketUri, new Listener(approvalKey))
                    .whenComplete((socket, error) -> {
                        if (error != null) {
                            connectionPending.set(false);
                            hub.updateProvider(PROVIDER, "ERROR",
                                    "KIS overseas WebSocket connection failed: " + safeMessage(error));
                            scheduleReconnect();
                        }
                    });
        } catch (RuntimeException exception) {
            connectionPending.set(false);
            hub.updateProvider(PROVIDER, "ERROR", "KIS overseas approval failed: " + safeMessage(exception));
            scheduleReconnect();
        }
    }

    private String approvalKey() {
        if (cachedApprovalKey != null && approvalKeyExpiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return cachedApprovalKey;
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
            throw new IllegalStateException("KIS approval_key is missing.");
        }
        cachedApprovalKey = key;
        approvalKeyExpiresAt = Instant.now().plus(Duration.ofHours(23));
        return key;
    }

    private String subscriptionMessage(String approvalKey, KisOverseasSubscription subscription, String type) {
        return objectMapper.writeValueAsString(Map.of(
                "header", Map.of(
                        "approval_key", approvalKey,
                        "custtype", "P",
                        "tr_type", type,
                        "content-type", "utf-8"),
                "body", Map.of("input", Map.of(
                        "tr_id", KisOverseasTradeMessageParser.TRANSACTION_ID,
                        "tr_key", subscription.trKey()))));
    }

    private KisOverseasSubscription subscriptionFor(String providerSymbol) {
        String normalized = providerSymbol == null ? "" : providerSymbol.trim().toUpperCase(java.util.Locale.ROOT);
        List<KisOverseasSubscription> matches = subscriptions.stream()
                .filter(subscription -> normalized.equals(subscription.symbol())
                        || normalized.equals(subscription.trKey())
                        || subscription.trKey().endsWith(normalized))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String sessionStatus(KisOverseasSubscription subscription, Instant asOf) {
        return "AUTO".equals(subscription.sessionStatus())
                ? usMarketSessionResolver.resolve(asOf).name()
                : subscription.sessionStatus();
    }

    private List<KisOverseasSubscription> normalize(List<KisOverseasSubscription> desired) {
        if (desired == null || desired.isEmpty()) {
            return List.of();
        }
        Map<String, KisOverseasSubscription> unique = new LinkedHashMap<>();
        desired.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(KisOverseasSubscription::market)
                        .thenComparing(KisOverseasSubscription::symbol))
                .forEach(subscription -> {
                    if (unique.size() < maxSubscriptions) {
                        unique.putIfAbsent(subscription.trKey(), subscription);
                    }
                });
        return List.copyOf(unique.values());
    }

    private Map<String, KisOverseasSubscription> byKey(List<KisOverseasSubscription> values) {
        Map<String, KisOverseasSubscription> result = new LinkedHashMap<>();
        values.forEach(subscription -> result.put(subscription.trKey(), subscription));
        return result;
    }

    private void confirmStream(WebSocket socket) {
        if (activeSocket != socket || streamConfirmed) {
            return;
        }
        streamConfirmed = true;
        reconnectAttempts.set(0);
        reconnectScheduled.set(false);
        hub.updateProvider(PROVIDER, "CONNECTED", connectionMessage(subscriptions.size()));
    }

    private void startWatchdog() {
        if (!watchdogStarted.compareAndSet(false, true)) {
            return;
        }
        long interval = Math.max(1_000, staleCheckInterval.toMillis());
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                checkLiveness(Instant.now());
            } catch (RuntimeException exception) {
                hub.updateProvider(PROVIDER, "DEGRADED", "KIS overseas liveness check failed: " + safeMessage(exception));
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void scheduleReconnect() {
        scheduleReconnect(null);
    }

    private void scheduleReconnect(String reason) {
        if (stopped || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        int attempt = reconnectAttempts.incrementAndGet();
        long delay = Math.min(Math.max(1, maxReconnectDelay.toSeconds()), 1L << Math.min(attempt - 1, 5));
        hub.updateProvider(PROVIDER, "RECONNECTING",
                (reason == null ? "Retrying KIS overseas connection" : reason) + " in " + delay + "s.");
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connect();
        }, delay, TimeUnit.SECONDS);
    }

    private String connectionMessage(int count) {
        return count == 0
                ? "KIS overseas connected; no US symbols subscribed."
                : count + " US symbol(s) subscribed through KIS HDFSCNT0.";
    }

    private String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message.replaceAll("(?i)(approval_key|token|appkey|secretkey)=[^&\\s]+", "$1=***");
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
                hub.updateProvider(PROVIDER, "DISCONNECTED", "KIS overseas connection closed: " + statusCode);
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
                hub.updateProvider(PROVIDER, "ERROR", "KIS overseas stream error: " + safeMessage(error));
                scheduleReconnect();
            }
        }
    }
}
