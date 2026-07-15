package com.finwatch.realtime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.finwatch.data.provider.FinnhubMarketDataClient;

import tools.jackson.databind.ObjectMapper;

@Component
public class FinnhubRealtimeClient {

    private static final String PROVIDER = "FINNHUB";

    private final String apiKey;
    private final URI websocketUri;
    private final Duration connectTimeout;
    private final Duration maxReconnectDelay;
    private final HttpClient websocketClient;
    private final ObjectMapper objectMapper;
    private final FinnhubMarketDataClient marketDataClient;
    private final FinnhubTradeMessageParser messageParser;
    private final RealtimeQuoteHub hub;
    private final ScheduledExecutorService scheduler;
    private final Map<String, BigDecimal> previousCloses = new ConcurrentHashMap<>();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicBoolean connectionPending = new AtomicBoolean();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();

    private volatile List<String> symbols = List.of();
    private volatile WebSocket activeSocket;
    private volatile boolean stopped = true;

    public FinnhubRealtimeClient(
            @Value("${app.data.finnhub.api-key:}") String apiKey,
            @Value("${app.data.finnhub.websocket-url:wss://ws.finnhub.io}") String websocketUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.realtime.reconnect-max-delay:30s}") Duration maxReconnectDelay,
            ObjectMapper objectMapper,
            FinnhubMarketDataClient marketDataClient,
            FinnhubTradeMessageParser messageParser,
            RealtimeQuoteHub hub) {
        this.apiKey = apiKey;
        this.websocketUri = URI.create(websocketUrl + "?token=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        this.connectTimeout = connectTimeout;
        this.maxReconnectDelay = maxReconnectDelay;
        this.websocketClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.objectMapper = objectMapper;
        this.marketDataClient = marketDataClient;
        this.messageParser = messageParser;
        this.hub = hub;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "finnhub-realtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start(List<String> symbols) {
        this.symbols = normalize(symbols);
        stopped = false;
        if (this.symbols.isEmpty()) {
            hub.updateProvider(PROVIDER, "IDLE", "구독할 미국 종목을 기다리는 중입니다.");
            return;
        }
        if (apiKey.isBlank()) {
            hub.updateProvider(PROVIDER, "ERROR", "FINNHUB_API_KEY가 필요합니다.");
            return;
        }
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
        if (apiKey.isBlank()) {
            hub.updateProvider(PROVIDER, "ERROR", "FINNHUB_API_KEY가 필요합니다.");
            return;
        }
        Set<String> previousSet = Set.copyOf(previous);
        Set<String> desiredSet = Set.copyOf(desired);
        List<String> additions = desired.stream().filter(symbol -> !previousSet.contains(symbol)).toList();
        List<String> removals = previous.stream().filter(symbol -> !desiredSet.contains(symbol)).toList();
        WebSocket socket = activeSocket;
        if (socket == null) {
            if (desired.isEmpty()) {
                hub.updateProvider(PROVIDER, "IDLE", "구독할 미국 종목을 기다리는 중입니다.");
            } else {
                scheduler.execute(() -> {
                    seedSnapshots(additions);
                    connect();
                });
            }
            return;
        }
        removals.forEach(symbol -> socket.sendText(subscriptionMessage("unsubscribe", symbol), true));
        seedSnapshots(additions);
        additions.forEach(symbol -> socket.sendText(subscriptionMessage("subscribe", symbol), true));
        hub.updateProvider(
                PROVIDER,
                desired.isEmpty() ? "IDLE" : "CONNECTED",
                desired.size() + "개 미국 종목 trade 구독 중");
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
                var quote = marketDataClient.quote(symbol);
                previousCloses.put(symbol, quote.price().subtract(quote.change()));
                hub.publish(new LiveQuote(
                        quote.symbol(),
                        quote.price(),
                        quote.change(),
                        quote.changeRate(),
                        quote.volume(),
                        quote.currency(),
                        quote.fetchedAt(),
                        "FINNHUB_REST",
                        "SNAPSHOT"));
            } catch (RuntimeException exception) {
                hub.updateProvider(PROVIDER, "DEGRADED", "초기 현재가 조회 실패: " + safeMessage(exception));
            }
        }
    }

    private void connect() {
        if (stopped || symbols.isEmpty() || !connectionPending.compareAndSet(false, true)) {
            return;
        }
        hub.updateProvider(PROVIDER, reconnectAttempts.get() == 0 ? "CONNECTING" : "RECONNECTING", "Finnhub trade stream 연결 중");
        websocketClient.newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(websocketUri, new Listener())
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        connectionPending.set(false);
                        hub.updateProvider(PROVIDER, "ERROR", "Finnhub WebSocket 연결 실패: " + safeMessage(error));
                        scheduleReconnect();
                    }
                });
    }

    private void handleMessage(String message) {
        for (var trade : messageParser.parse(message)) {
            if (!symbols.contains(trade.symbol())) {
                continue;
            }
            BigDecimal previousClose = previousCloses.get(trade.symbol());
            BigDecimal change = previousClose == null
                    ? BigDecimal.ZERO
                    : trade.price().subtract(previousClose);
            BigDecimal changeRate = previousClose == null || previousClose.signum() == 0
                    ? BigDecimal.ZERO
                    : change.multiply(BigDecimal.valueOf(100)).divide(previousClose, 4, RoundingMode.HALF_UP);
            hub.publish(new LiveQuote(
                    trade.symbol(),
                    trade.price(),
                    change,
                    changeRate,
                    trade.volume(),
                    "USD",
                    trade.asOf(),
                    "FINNHUB_WS",
                    "LIVE"));
        }
    }

    private void scheduleReconnect() {
        if (stopped || symbols.isEmpty() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        int attempt = reconnectAttempts.incrementAndGet();
        long maxSeconds = Math.max(1, maxReconnectDelay.toSeconds());
        long delay = Math.min(maxSeconds, 1L << Math.min(attempt - 1, 5));
        hub.updateProvider(PROVIDER, "RECONNECTING", delay + "초 후 다시 연결합니다.");
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connect();
        }, delay, TimeUnit.SECONDS);
    }

    private String subscribeMessage(String symbol) {
        return subscriptionMessage("subscribe", symbol);
    }

    private String subscriptionMessage(String type, String symbol) {
        return objectMapper.writeValueAsString(Map.of("type", type, "symbol", symbol));
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .toList()));
    }

    private String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message.replaceAll("token=[^&\\s]+", "token=***");
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            connectionPending.set(false);
            activeSocket = webSocket;
            reconnectAttempts.set(0);
            reconnectScheduled.set(false);
            webSocket.request(1);
            CompletableFuture<?> subscriptions = CompletableFuture.completedFuture(null);
            for (String symbol : symbols) {
                subscriptions = subscriptions.thenCompose(ignored -> webSocket.sendText(subscribeMessage(symbol), true));
            }
            subscriptions.whenComplete((ignored, error) -> {
                if (error == null) {
                    hub.updateProvider(PROVIDER, "CONNECTED", symbols.size() + "개 미국 종목 trade 구독 중");
                } else {
                    hub.updateProvider(PROVIDER, "ERROR", "Finnhub 구독 요청 실패: " + safeMessage(error));
                    webSocket.abort();
                    scheduleReconnect();
                }
            });
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connectionPending.set(false);
            if (activeSocket == webSocket) {
                activeSocket = null;
            }
            if (!stopped) {
                hub.updateProvider(PROVIDER, "DISCONNECTED", "Finnhub 연결 종료: " + statusCode);
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connectionPending.set(false);
            if (activeSocket == webSocket) {
                activeSocket = null;
            }
            if (!stopped) {
                hub.updateProvider(PROVIDER, "ERROR", "Finnhub 스트림 오류: " + safeMessage(error));
                scheduleReconnect();
            }
        }
    }
}
