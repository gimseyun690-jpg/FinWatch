package com.finwatch.fx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.finwatch.fx.cache.FxRateCacheStore;
import com.finwatch.fx.domain.ExchangeRate;
import com.finwatch.fx.realtime.FinnhubFxRealtimeStore;
import com.finwatch.fx.repository.ExchangeRateRepository;

class FxRateServiceTest {

    @Test
    void prefersARecentProviderTimestampedRealtimeQuote() {
        ExchangeRateRepository repository = mock(ExchangeRateRepository.class);
        FxRateCacheStore cache = mock(FxRateCacheStore.class);
        FinnhubFxRealtimeStore realtimeStore = new FinnhubFxRealtimeStore();
        Instant now = Instant.now();
        realtimeStore.accept(
                "OANDA:USD_KRW",
                new BigDecimal("1410.50"),
                now.minusSeconds(5),
                now);
        ExchangeRate previous = ExchangeRate.create(
                "USD", "KRW", new BigDecimal("1400.00"),
                null, null, null, new BigDecimal("1400.00"),
                "REFERENCE", "FRANKFURTER", "FRANKFURTER:USD/KRW",
                now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)));
        when(repository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceAndAsOfBeforeOrderByAsOfDesc(
                anyString(), anyString(), anyString(), any(Instant.class)))
                .thenReturn(Optional.of(previous));

        FxRateService service = new FxRateService(
                repository,
                List.of(),
                cache,
                realtimeStore,
                "LIVE",
                Duration.ofSeconds(60),
                Duration.ofMinutes(15),
                Duration.ofHours(36),
                Duration.ofMinutes(2),
                "OANDA:USD_KRW");

        var result = service.latest("USD", "KRW");

        assertThat(result.rate()).isEqualByComparingTo("1410.50");
        assertThat(result.previousClose()).isEqualByComparingTo("1400.00");
        assertThat(result.rateType()).isEqualTo("LIVE");
        assertThat(result.source()).isEqualTo("FINNHUB_WS");
        assertThat(result.freshness()).isEqualTo("FRESH");
    }
}
