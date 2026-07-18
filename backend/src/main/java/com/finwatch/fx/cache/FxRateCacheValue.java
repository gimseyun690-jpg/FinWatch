package com.finwatch.fx.cache;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record FxRateCacheValue(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        String rateType,
        String source,
        String providerSymbol,
        Instant asOf,
        Instant fetchedAt) implements Serializable {
}
