package com.finwatch.ai.provider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.finwatch.ai.dto.TechnicalExplanationInput;
import com.finwatch.ai.dto.DailyChangeBriefingInput;
import com.finwatch.ai.dto.TechnicalExplanationInput.TechnicalEvidence;
import com.finwatch.ai.provider.AiProvider.TechnicalSignalExplanation;
import com.finwatch.ai.provider.AiProvider.DailyBriefingStatement;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    @Override
    public AiProviderResult summarize(
            String title,
            String preprocessedContent,
            String segmentId,
            String promptVersion) {
        List<String> sentences = List.of(preprocessedContent.split("(?<=[.!?])\\s+"));
        List<String> keyPoints = sentences.stream().filter(sentence -> !sentence.isBlank()).limit(3).toList();
        String summary = String.join(" ", keyPoints);
        if (summary.isBlank()) {
            summary = title + " 관련 핵심 내용을 확인했습니다.";
        }

        Set<String> keywords = new LinkedHashSet<>();
        for (String candidate : List.of("HBM", "메모리", "AI", "반도체", "실적", "공급망")) {
            if ((title + " " + preprocessedContent).contains(candidate)) {
                keywords.add(candidate);
            }
        }
        if (keywords.isEmpty()) {
            keywords.add("시장 이슈");
        }

        String sentiment = containsAny(preprocessedContent, "증가", "확대", "회복", "성장", "상향")
                ? "POSITIVE"
                : containsAny(preprocessedContent, "감소", "하락", "위험", "둔화") ? "NEGATIVE" : "NEUTRAL";
        List<String> positiveFactors = containsAny(preprocessedContent, "증가", "확대", "회복", "성장", "상향")
                ? List.of("수요와 실적 개선 가능성이 언급되었습니다.")
                : List.of();
        List<String> riskFactors = containsAny(preprocessedContent, "위험", "변동", "경쟁", "둔화", "감소")
                ? List.of("시장 변동성과 경쟁 환경을 함께 확인해야 합니다.")
                : List.of("기사에 구체적인 위험 요인이 충분히 제시되지 않았습니다.");
        List<String> mentionedCompanies = (title + " " + preprocessedContent).contains("SK하이닉스")
                ? List.of("SK하이닉스")
                : List.of();
        int inputTokens = estimateTokens(title + preprocessedContent + segmentId + promptVersion);
        int outputTokens = estimateTokens(summary + String.join(" ", keywords));

        return new AiProviderResult(
                "finwatch-demo-analyzer-v2",
                summary,
                new ArrayList<>(keyPoints),
                positiveFactors,
                riskFactors,
                mentionedCompanies,
                new ArrayList<>(keywords.stream().limit(5).toList()),
                sentiment,
                inputTokens,
                outputTokens);
    }

    @Override
    public TechnicalExplanationResult explainTechnical(
            TechnicalExplanationInput input,
            String promptVersion) {
        TechnicalEvidence movingAverage = evidence(input, "MOVING_AVERAGE");
        TechnicalEvidence rsi = evidence(input, "RSI");
        TechnicalEvidence macd = evidence(input, "MACD");
        TechnicalEvidence atr = evidence(input, "ATR");
        TechnicalEvidence volume = evidence(input, "VOLUME");

        List<TechnicalSignalExplanation> supporting = new ArrayList<>();
        supporting.add(new TechnicalSignalExplanation(
                "이동평균 관계가 현재 서버 신호의 주요 근거입니다.",
                List.of(movingAverage.id())));
        supporting.add(new TechnicalSignalExplanation(
                "MACD 값과 히스토그램이 모멘텀 방향을 보여줍니다.",
                List.of(macd.id())));

        List<TechnicalSignalExplanation> conflicting = "NEUTRAL".equals(input.summarySignal())
                ? List.of(new TechnicalSignalExplanation(
                        "지표들의 방향이 한쪽으로 충분히 모이지 않았습니다.",
                        List.of(movingAverage.id(), rsi.id(), macd.id())))
                : List.of(new TechnicalSignalExplanation(
                        "추세 신호와 RSI의 과열·침체 경계는 별도로 확인해야 합니다.",
                        List.of(movingAverage.id(), rsi.id())));

        List<String> limitations = new ArrayList<>();
        if ("DEMO".equals(input.freshness()) || "DEMO".equalsIgnoreCase(input.source())) {
            limitations.add("DEMO 데이터이므로 실제 투자 판단에 사용할 수 없습니다.");
        } else if ("STALE".equals(input.freshness())) {
            limitations.add("STALE 데이터가 포함되어 최신 시장 상태와 다를 수 있습니다.");
        }

        String serializedInput = input.toString() + promptVersion;
        String outputText = movingAverage.displayValue() + rsi.displayValue() + macd.displayValue();
        return new TechnicalExplanationResult(
                "finwatch-demo-technical-v1",
                "서버가 계산한 기술지표는 " + input.summarySignal()
                        + " 흐름을 보이지만 추세·모멘텀·변동성 신호를 함께 확인해야 합니다.",
                movingAverage.displayValue() + " 관계를 기준으로 추세를 해석했습니다.",
                rsi.displayValue() + ", " + macd.displayValue() + "를 함께 보면 모멘텀의 강도와 충돌을 확인할 수 있습니다.",
                atr.displayValue() + "를 기준으로 현재 변동성 수준을 확인해야 합니다.",
                volume.displayValue() + "로 가격 움직임이 거래량의 확인을 받는지 살펴볼 수 있습니다.",
                supporting,
                conflicting,
                List.of("기술지표는 과거 가격을 계산한 값이며 미래 가격이나 수익을 보장하지 않습니다."),
                limitations,
                estimateTokens(serializedInput),
                estimateTokens(outputText));
    }

    @Override
    public DailyBriefingResult generateDailyBriefing(DailyChangeBriefingInput input, String promptVersion) {
        String priceEvidence = "T1";
        String momentumEvidence = "T4";
        List<String> limitations = new ArrayList<>(input.serverDataLimitations());
        List<DailyBriefingStatement> strengths = input.priceChange().signum() > 0
                ? List.of(new DailyBriefingStatement("전일보다 종가 흐름이 높아졌습니다.", List.of(priceEvidence)))
                : List.of();
        List<DailyBriefingStatement> risks = input.viewpoints().stream().anyMatch(item -> "CAUTION".equals(item.status()))
                ? List.of(new DailyBriefingStatement("주의 상태인 관점을 함께 확인해야 합니다.", input.viewpoints().stream()
                        .filter(item -> "CAUTION".equals(item.status())).flatMap(item -> item.evidenceIds().stream()).distinct().limit(4).toList()))
                : List.of();
        List<DailyBriefingStatement> conflicts = "CONFLICTING".equals(input.relation())
                ? List.of(new DailyBriefingStatement("긍정 흐름과 주의 신호가 동시에 존재합니다.", List.of(priceEvidence, momentumEvidence)))
                : List.of();
        String headline = "CONFLICTING".equals(input.relation())
                ? "전일 대비 변화에 긍정 흐름과 주의 신호가 함께 나타났습니다."
                : "전일 대비 가격과 기술지표 변화를 근거로 정리했습니다.";
        String output = headline + input.relation() + strengths + risks + conflicts;
        return new DailyBriefingResult("finwatch-demo-daily-briefing-v1", headline, List.of(priceEvidence, momentumEvidence),
                "서버가 계산한 전일 대비 변화와 관점별 상태를 비교했습니다.", List.of("T1", "T3", "T4"),
                strengths, risks,
                List.of(new DailyBriefingStatement("분석 방식과 계산 버전은 동일하게 유지됩니다.", List.of("Q1"))),
                "ALIGNED".equals(input.relation()) ? List.of(new DailyBriefingStatement("여러 관점이 같은 방향을 보입니다.", List.of("T1", "T2"))) : List.of(),
                conflicts, limitations, estimateTokens(input.toString() + promptVersion), estimateTokens(output));
    }

    private TechnicalEvidence evidence(TechnicalExplanationInput input, String indicator) {
        return input.evidence().stream()
                .filter(item -> indicator.equals(item.indicator()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(indicator + " 근거가 없습니다."));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 3.2));
    }
}
