package com.finwatch.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.finwatch.portfolio.dto.PortfolioResponse;
import com.finwatch.portfolio.dto.PortfolioResponse.CurrencySummary;
import com.finwatch.portfolio.dto.PortfolioResponse.Holding;
import com.finwatch.portfolio.service.PortfolioService;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;

import tools.jackson.databind.ObjectMapper;

class PortfolioEvaluationSnapshotFactoryTest {

    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-07-30T05:37:12Z");

    @Test
    void computesConcentrationAndKeepsPositionHashStableAcrossTicks() {
        PortfolioService portfolios = mock(PortfolioService.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser user = mock(AppUser.class);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(portfolios.getPortfolio(USER_ID))
                .thenReturn(portfolio("60", "40"))
                .thenReturn(portfolio("61", "39"));
        PortfolioEvaluationSnapshotFactory factory = new PortfolioEvaluationSnapshotFactory(
                portfolios,
                users,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var first = factory.create(USER_ID);
        var second = factory.create(USER_ID);

        assertThat(first.input().windowStartedAt())
                .isEqualTo(Instant.parse("2026-07-30T05:30:00Z"));
        assertThat(first.input().topPositionWeight()).isEqualByComparingTo("60.0000");
        assertThat(first.input().topThreeWeight()).isEqualByComparingTo("100.0000");
        assertThat(first.input().concentrationHhi()).isEqualByComparingTo("5200.00");
        assertThat(first.input().concentrationBand()).isEqualTo("HIGH_CONCENTRATION");
        assertThat(first.positionsHash()).isEqualTo(second.positionsHash());
        assertThat(first.inputHash()).isNotEqualTo(second.inputHash());
    }

    @Test
    void rejectsEmptyPortfolio() {
        PortfolioService portfolios = mock(PortfolioService.class);
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(USER_ID)).thenReturn(Optional.of(mock(AppUser.class)));
        when(portfolios.getPortfolio(USER_ID)).thenReturn(new PortfolioResponse(
                "KRW",
                null,
                null,
                null,
                true,
                true,
                List.of(),
                List.of(),
                List.of()));
        PortfolioEvaluationSnapshotFactory factory = new PortfolioEvaluationSnapshotFactory(
                portfolios,
                users,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> factory.create(USER_ID))
                .isInstanceOfSatisfying(PortfolioEvaluationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("AI_PORTFOLIO_EMPTY"));
    }

    private PortfolioResponse portfolio(String firstEvaluation, String secondEvaluation) {
        BigDecimal first = new BigDecimal(firstEvaluation);
        BigDecimal second = new BigDecimal(secondEvaluation);
        List<Holding> holdings = List.of(
                holding(1L, "AAA", "첫 종목", first, new BigDecimal("50")),
                holding(2L, "BBB", "둘째 종목", second, new BigDecimal("50")));
        return new PortfolioResponse(
                "KRW",
                new BigDecimal("100"),
                new BigDecimal("100"),
                BigDecimal.ZERO,
                true,
                true,
                List.of(),
                List.of(new CurrencySummary(
                        "KRW",
                        new BigDecimal("100"),
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        true)),
                holdings);
    }

    private Holding holding(
            Long id,
            String symbol,
            String name,
            BigDecimal evaluation,
            BigDecimal purchase) {
        Instant priceAsOf = Instant.parse("2026-07-30T05:36:00Z");
        Instant holdingUpdatedAt = Instant.parse("2026-07-01T00:00:00Z");
        return new Holding(
                id,
                symbol,
                name,
                "KRX",
                "KRW",
                BigDecimal.ONE,
                purchase,
                null,
                null,
                null,
                evaluation,
                priceAsOf,
                "TEST",
                purchase,
                evaluation,
                evaluation.subtract(purchase),
                evaluation.subtract(purchase)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(purchase),
                evaluation,
                purchase,
                evaluation.subtract(purchase),
                null,
                "VALUED",
                holdingUpdatedAt);
    }
}
