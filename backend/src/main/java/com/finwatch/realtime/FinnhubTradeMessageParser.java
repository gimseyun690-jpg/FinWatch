package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
public class FinnhubTradeMessageParser {

    private final ObjectMapper objectMapper;

    public FinnhubTradeMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<FinnhubTrade> parse(String message) {
        try {
            var root = objectMapper.readTree(message);
            if (!"trade".equals(root.path("type").asText()) || !root.path("data").isArray()) {
                return List.of();
            }
            List<FinnhubTrade> trades = new ArrayList<>();
            for (var item : root.path("data")) {
                String symbol = item.path("s").asText("").trim().toUpperCase(java.util.Locale.ROOT);
                BigDecimal price = item.path("p").decimalValue();
                if (symbol.isBlank() || price.signum() <= 0) {
                    continue;
                }
                long epochMillis = item.path("t").asLong(0);
                trades.add(new FinnhubTrade(
                        symbol,
                        price,
                        item.path("v").decimalValue(),
                        epochMillis > 0 ? Instant.ofEpochMilli(epochMillis) : Instant.now(),
                        epochMillis > 0));
            }
            return List.copyOf(trades);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public record FinnhubTrade(
            String symbol,
            BigDecimal price,
            BigDecimal volume,
            Instant asOf,
            boolean providerTimestamp) {
    }
}
