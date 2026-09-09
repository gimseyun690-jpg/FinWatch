package com.finwatch.realtime;

import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class MarketSessionResolver {

    private final UsMarketSessionResolver usResolver;
    private final KrxMarketSessionResolver krxResolver;

    public MarketSessionResolver(
            UsMarketSessionResolver usResolver,
            KrxMarketSessionResolver krxResolver) {
        this.usResolver = usResolver;
        this.krxResolver = krxResolver;
    }

    public MarketSessionStatus resolve(String market, Instant tradeTime) {
        if (market == null || tradeTime == null) {
            return MarketSessionStatus.UNKNOWN;
        }
        String normalized = market.trim().toUpperCase(Locale.ROOT);
        if ("KRX".equals(normalized) || "KOSPI".equals(normalized) || "KOSDAQ".equals(normalized) || "KONEX".equals(normalized)) {
            return krxResolver.resolve(tradeTime);
        }
        return usResolver.resolve(tradeTime);
    }
}
