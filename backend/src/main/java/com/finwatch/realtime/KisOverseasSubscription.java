package com.finwatch.realtime;

import java.util.Locale;

/**
 * A KIS overseas real-time subscription. The KIS market prefix is part of the
 * wire key and must not be inferred from the broker's display market name.
 */
public record KisOverseasSubscription(
        String market,
        String symbol,
        String trKey,
        String sessionStatus) {

    public KisOverseasSubscription {
        market = normalize(market);
        symbol = normalize(symbol);
        trKey = normalize(trKey);
        sessionStatus = normalize(sessionStatus);
        if (market.isBlank() || symbol.isBlank() || trKey.isBlank()) {
            throw new IllegalArgumentException("KIS overseas subscription requires market, symbol, and trKey");
        }
    }

    public static KisOverseasSubscription standard(String market, String symbol) {
        String normalizedMarket = normalize(market);
        return new KisOverseasSubscription(
                normalizedMarket,
                symbol,
                standardPrefix(normalizedMarket) + normalize(symbol),
                "AUTO");
    }

    private static String standardPrefix(String market) {
        return switch (market) {
            case "NASDAQ" -> "DNAS";
            case "NYSE" -> "DNYS";
            case "AMEX" -> "DAMS";
            default -> throw new IllegalArgumentException("Unsupported KIS overseas market: " + market);
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
