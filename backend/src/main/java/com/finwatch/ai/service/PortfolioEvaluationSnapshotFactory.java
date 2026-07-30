package com.finwatch.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.finwatch.ai.dto.PortfolioEvaluationInput;
import com.finwatch.ai.dto.PortfolioEvaluationInput.CurrencyExposure;
import com.finwatch.ai.dto.PortfolioEvaluationInput.Evidence;
import com.finwatch.ai.dto.PortfolioEvaluationInput.Position;
import com.finwatch.portfolio.dto.PortfolioResponse;
import com.finwatch.portfolio.dto.PortfolioResponse.Holding;
import com.finwatch.portfolio.service.PortfolioService;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;

import tools.jackson.databind.ObjectMapper;

@Component
public class PortfolioEvaluationSnapshotFactory {

    static final int WINDOW_SECONDS = 15 * 60;
    private static final int MAX_PROVIDER_POSITIONS = 20;

    private final PortfolioService portfolioService;
    private final AppUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public PortfolioEvaluationSnapshotFactory(
            PortfolioService portfolioService,
            AppUserRepository userRepository,
            ObjectMapper objectMapper) {
        this(portfolioService, userRepository, objectMapper, Clock.systemUTC());
    }

    PortfolioEvaluationSnapshotFactory(
            PortfolioService portfolioService,
            AppUserRepository userRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.portfolioService = portfolioService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public SnapshotBundle create(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new PortfolioEvaluationException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "인증된 사용자를 찾을 수 없습니다."));
        PortfolioResponse portfolio = portfolioService.getPortfolio(userId);
        if (portfolio.holdings().isEmpty()) {
            throw new PortfolioEvaluationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "AI_PORTFOLIO_EMPTY",
                    "AI 평가를 생성하려면 보유 종목을 먼저 등록해 주세요.");
        }

        Instant snapshotAt = clock.instant();
        Instant windowStartedAt = Instant.ofEpochSecond(
                Math.floorDiv(snapshotAt.getEpochSecond(), WINDOW_SECONDS) * WINDOW_SECONDS);
        List<Holding> sorted = portfolio.holdings().stream()
                .sorted(Comparator
                        .comparing(Holding::convertedEvaluationAmount,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Holding::market)
                        .thenComparing(Holding::symbol))
                .toList();
        String positionsHash = sha256(sorted.stream()
                .sorted(Comparator.comparing(Holding::market).thenComparing(Holding::symbol))
                .map(this::identity)
                .toList(), "포트폴리오 보유 구성");

        boolean weightComplete = portfolio.conversionComplete()
                && portfolio.baseCurrencyTotalEvaluationAmount() != null
                && portfolio.baseCurrencyTotalEvaluationAmount().signum() > 0;
        BigDecimal totalEvaluation = weightComplete
                ? portfolio.baseCurrencyTotalEvaluationAmount()
                : null;
        List<Position> positions = sorted.stream()
                .limit(MAX_PROVIDER_POSITIONS)
                .map(holding -> position(holding, totalEvaluation))
                .toList();
        List<CurrencyExposure> currencyExposures = currencyExposures(sorted, totalEvaluation);
        BigDecimal topWeight = weightComplete
                ? weight(sorted.getFirst().convertedEvaluationAmount(), totalEvaluation)
                : null;
        BigDecimal topThreeWeight = weightComplete
                ? sorted.stream().limit(3).map(Holding::convertedEvaluationAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalEvaluation, 4, RoundingMode.HALF_UP)
                : null;
        BigDecimal hhi = weightComplete
                ? sorted.stream()
                        .map(Holding::convertedEvaluationAmount)
                        .map(value -> weight(value, totalEvaluation))
                        .map(value -> value.multiply(value))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP)
                : null;
        String concentrationBand = concentrationBand(hhi);
        BigDecimal returnRate = portfolio.profitLossComplete()
                && portfolio.baseCurrencyTotalPurchaseAmount() != null
                && portfolio.baseCurrencyTotalPurchaseAmount().signum() > 0
                && portfolio.baseCurrencyProfitLoss() != null
                ? portfolio.baseCurrencyProfitLoss().multiply(BigDecimal.valueOf(100))
                        .divide(portfolio.baseCurrencyTotalPurchaseAmount(), 4, RoundingMode.HALF_UP)
                : null;
        List<String> limitations = limitations(portfolio, sorted, weightComplete);
        List<Evidence> evidence = evidence(
                portfolio,
                positions,
                currencyExposures,
                topWeight,
                topThreeWeight,
                hhi,
                concentrationBand,
                returnRate);
        PortfolioEvaluationInput input = new PortfolioEvaluationInput(
                portfolio.baseCurrency(),
                snapshotAt,
                windowStartedAt,
                sorted.size(),
                (int) sorted.stream().filter(item -> "VALUED".equals(item.valuationStatus())).count(),
                portfolio.conversionComplete(),
                portfolio.profitLossComplete(),
                portfolio.baseCurrencyTotalEvaluationAmount(),
                portfolio.baseCurrencyTotalPurchaseAmount(),
                portfolio.baseCurrencyProfitLoss(),
                returnRate,
                topWeight,
                topThreeWeight,
                hhi,
                concentrationBand,
                currencyExposures,
                positions,
                evidence,
                limitations);
        return new SnapshotBundle(user, input, sha256(input, "포트폴리오 평가 입력"), positionsHash);
    }

    private PositionIdentity identity(Holding holding) {
        return new PositionIdentity(
                holding.id(),
                holding.symbol(),
                holding.market(),
                holding.currency(),
                holding.quantity(),
                holding.averagePurchasePrice(),
                holding.averagePurchaseFxRate(),
                holding.purchaseFxBaseCurrency(),
                holding.purchaseFxQuoteCurrency(),
                holding.updatedAt());
    }

    private Position position(Holding holding, BigDecimal totalEvaluation) {
        return new Position(
                holding.symbol(),
                holding.name(),
                holding.market(),
                holding.currency(),
                holding.evaluationAmount(),
                holding.convertedEvaluationAmount(),
                totalEvaluation == null ? null : weight(holding.convertedEvaluationAmount(), totalEvaluation),
                holding.returnRate(),
                holding.valuationStatus());
    }

    private List<CurrencyExposure> currencyExposures(List<Holding> holdings, BigDecimal totalEvaluation) {
        Map<String, List<Holding>> grouped = new LinkedHashMap<>();
        holdings.stream()
                .sorted(Comparator.comparing(Holding::currency))
                .forEach(holding -> grouped.computeIfAbsent(holding.currency(), ignored -> new ArrayList<>())
                        .add(holding));
        return grouped.entrySet().stream().map(entry -> {
            boolean complete = totalEvaluation != null
                    && entry.getValue().stream().allMatch(item -> item.convertedEvaluationAmount() != null);
            BigDecimal evaluation = complete
                    ? entry.getValue().stream().map(Holding::convertedEvaluationAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : null;
            return new CurrencyExposure(
                    entry.getKey(),
                    evaluation,
                    evaluation == null ? null : weight(evaluation, totalEvaluation),
                    entry.getValue().size());
        }).toList();
    }

    private List<String> limitations(
            PortfolioResponse portfolio,
            List<Holding> holdings,
            boolean weightComplete) {
        List<String> values = new ArrayList<>();
        if (!portfolio.conversionComplete()) {
            values.add("일부 가격 또는 USD/KRW 환율이 없어 서로 다른 통화를 합산한 비중을 계산하지 않았습니다.");
        } else if (!weightComplete) {
            values.add("기준통화 총 평가액이 0 이하이므로 종목별 비중과 집중도를 계산하지 않았습니다.");
        }
        if (!portfolio.profitLossComplete()) {
            values.add("일부 매수 당시 환율이 없어 기준통화 손익과 통합 수익률이 불완전합니다.");
        }
        long unavailable = holdings.stream()
                .filter(item -> !"VALUED".equals(item.valuationStatus()))
                .count();
        if (unavailable > 0) {
            values.add("가격을 확인할 수 없는 보유 종목이 " + unavailable + "개 있습니다.");
        }
        if (holdings.stream().anyMatch(item -> item.priceSource() != null
                && item.priceSource().toUpperCase().contains("DEMO"))) {
            values.add("DEMO 가격이 포함되어 실제 시장 포트폴리오 평가로 사용할 수 없습니다.");
        }
        if (portfolio.fxRates().stream().anyMatch(rate ->
                !"FRESH".equalsIgnoreCase(rate.freshness()))) {
            values.add("최신성이 제한된 환율이 포함되어 원화 환산값이 현재 시장과 다를 수 있습니다.");
        }
        if (holdings.size() > MAX_PROVIDER_POSITIONS) {
            values.add("AI에는 비중 상위 " + MAX_PROVIDER_POSITIONS
                    + "개 종목만 전달했으며 집중도 계산에는 전체 보유 종목을 사용했습니다.");
        }
        return List.copyOf(values);
    }

    private List<Evidence> evidence(
            PortfolioResponse portfolio,
            List<Position> positions,
            List<CurrencyExposure> currencies,
            BigDecimal topWeight,
            BigDecimal topThreeWeight,
            BigDecimal hhi,
            String concentrationBand,
            BigDecimal returnRate) {
        List<Evidence> values = new ArrayList<>();
        values.add(new Evidence("P1", "PORTFOLIO_TOTAL", map(
                "baseCurrency", portfolio.baseCurrency(),
                "evaluationAmount", number(portfolio.baseCurrencyTotalEvaluationAmount()),
                "purchaseAmount", number(portfolio.baseCurrencyTotalPurchaseAmount()),
                "profitLoss", number(portfolio.baseCurrencyProfitLoss()),
                "returnRate", number(returnRate)),
                "기준통화 " + portfolio.baseCurrency()
                        + " · 평가액 " + number(portfolio.baseCurrencyTotalEvaluationAmount())
                        + " · 손익 " + number(portfolio.baseCurrencyProfitLoss())
                        + " · 수익률 " + number(returnRate) + "%"));
        values.add(new Evidence("C1", "CONCENTRATION", map(
                "holdingCount", Integer.toString(portfolio.holdings().size()),
                "topPositionWeight", number(topWeight),
                "topThreeWeight", number(topThreeWeight),
                "hhi", number(hhi),
                "band", concentrationBand),
                "보유 " + portfolio.holdings().size() + "개 · 최대 종목 "
                        + number(topWeight) + "% · 상위 3종목 "
                        + number(topThreeWeight) + "% · HHI " + number(hhi)
                        + " · " + concentrationBand));
        Map<String, String> currencyValues = new LinkedHashMap<>();
        for (CurrencyExposure currency : currencies) {
            currencyValues.put(currency.currency() + "Weight", number(currency.weightPercent()));
            currencyValues.put(currency.currency() + "Holdings", Integer.toString(currency.holdingCount()));
        }
        values.add(new Evidence(
                "FX1",
                "CURRENCY_EXPOSURE",
                currencyValues,
                currencies.stream()
                        .map(item -> item.currency() + " " + number(item.weightPercent()) + "%")
                        .reduce((first, second) -> first + " · " + second)
                        .orElse("통화 비중 없음")));
        for (int index = 0; index < positions.size(); index++) {
            Position position = positions.get(index);
            values.add(new Evidence(
                    "H" + (index + 1),
                    "HOLDING",
                    map(
                            "symbol", position.symbol(),
                            "market", position.market(),
                            "currency", position.currency(),
                            "weightPercent", number(position.weightPercent()),
                            "returnRate", number(position.returnRate()),
                            "valuationStatus", position.valuationStatus()),
                    position.name() + " · 비중 " + number(position.weightPercent())
                            + "% · 수익률 " + number(position.returnRate()) + "%"));
        }
        return List.copyOf(values);
    }

    private BigDecimal weight(BigDecimal value, BigDecimal total) {
        if (value == null || total == null || total.signum() == 0) return null;
        return value.multiply(BigDecimal.valueOf(100))
                .divide(total, 4, RoundingMode.HALF_UP);
    }

    private String concentrationBand(BigDecimal hhi) {
        if (hhi == null) return "INCOMPLETE";
        if (hhi.compareTo(BigDecimal.valueOf(1_500)) < 0) return "DIVERSIFIED";
        if (hhi.compareTo(BigDecimal.valueOf(2_500)) <= 0) return "MODERATE_CONCENTRATION";
        return "HIGH_CONCENTRATION";
    }

    private Map<String, String> map(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return values;
    }

    private String number(BigDecimal value) {
        return value == null ? "N/A" : value.stripTrailingZeros().toPlainString();
    }

    private String sha256(Object value, String label) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) {
            throw new IllegalStateException(label + " hash를 계산할 수 없습니다.", exception);
        }
    }

    private record PositionIdentity(
            Long holdingId,
            String symbol,
            String market,
            String currency,
            BigDecimal quantity,
            BigDecimal averagePurchasePrice,
            BigDecimal averagePurchaseFxRate,
            String purchaseFxBaseCurrency,
            String purchaseFxQuoteCurrency,
            Instant updatedAt) {
    }

    public record SnapshotBundle(
            AppUser user,
            PortfolioEvaluationInput input,
            String inputHash,
            String positionsHash) {
    }
}
