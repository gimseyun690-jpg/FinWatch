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

        String currency = input.currency();
        String price = getMoney(movingAverage, "price", currency);
        String ma5 = getMoney(movingAverage, "ma5", currency);
        String ma20 = getMoney(movingAverage, "ma20", currency);
        String ma60 = getMoney(movingAverage, "ma60", currency);
        String rsiValue = getFloat(rsi, "value");
        String macdVal = getFloat(macd, "value");
        String macdSig = getFloat(macd, "signal");
        String macdHist = getFloat(macd, "histogram");
        String atrVal = getMoney(atr, "value", currency);
        String atrPct = getPercent(atr, "percent");
        String volVal = getNumber(volume, "current");
        String volMa = getNumber(volume, "ma20");
        String volRatio = getPercent(volume, "ratio");

        String summary = "서버의 기술분석 결과, 현재 종합 신호는 " 
                + ("BUY".equals(input.summarySignal()) ? "매수 우위" : "SELL".equals(input.summarySignal()) ? "매도 우위" : "중립") 
                + " 상태입니다. 주요 이동평균선 지지와 오실레이터의 수렴 방향이 일치하는지 분석했습니다.";

        String trendText = "현재 주가는 " + price + "으로, 단기 이평선(MA5: " + ma5 + ") 및 중기 이평선(MA20: " + ma20 
                + ") 아래에 위치하고 있습니다. 장기 이평선(MA60: " + ma60 + ")을 포함한 전반적인 흐름이 " 
                + ("BUY".equals(input.summarySignal()) ? "상승" : "하락") + " 압력을 강하게 보이고 있습니다.";

        String momentumText = "RSI14 수치가 " + rsiValue + "로 중립 범위에 머물러 있으나, MACD선(" + macdVal 
                + ")이 시그널선(" + macdSig + ") 대비 " 
                + ("BUY".equals(macd.values().get("signal")) ? "상승 돌파(골든크로스)" : "하락 돌파(데드크로스)") 
                + " 흐름을 보이며 히스토그램(" + macdHist + ")이 음의 권역에서 움직이고 있어 단기 매도 모멘텀이 지배적입니다.";

        String volatilityText = "ATR14 기준 최근 평균 일간 변동폭은 " + atrVal + "로, 현재 주가 대비 약 " + atrPct 
                + "% 수준입니다. 변동성이 다소 높은 국면이므로 무리한 추격 매수보다는 분할 진입이 권장됩니다.";

        String volumeText = "당일 거래량(" + volVal + "주)은 20일 평균 거래량(" + volMa + "주) 대비 약 " + volRatio 
                + "배 수준에 머물고 있습니다. 가격 변동 대비 거래량이 급증하지 않은 횡보성 국면입니다.";

        List<TechnicalSignalExplanation> supporting = new ArrayList<>();
        supporting.add(new TechnicalSignalExplanation(
                "이동평균 정배열/역배열 관계 및 주가 위치가 주요 추세 지지 근거입니다.",
                List.of(movingAverage.id())));
        supporting.add(new TechnicalSignalExplanation(
                "MACD 오실레이터의 크로스 여부와 히스토그램 확장이 주요 모멘텀 근거입니다.",
                List.of(macd.id())));

        List<TechnicalSignalExplanation> conflicting = "NEUTRAL".equals(input.summarySignal())
                ? List.of(new TechnicalSignalExplanation(
                        "추세 지표와 오실레이터 지표들의 방향이 서로 엇갈려 중립을 나타냅니다.",
                        List.of(movingAverage.id(), rsi.id(), macd.id())))
                : List.of(new TechnicalSignalExplanation(
                        "추세선 돌파 흐름과 RSI의 과매수/과매도 경계 영역 진입은 서로 모순되는 신호를 보일 수 있어 주의가 필요합니다.",
                        List.of(movingAverage.id(), rsi.id())));

        List<String> limitations = new ArrayList<>();
        if ("DEMO".equals(input.freshness()) || "DEMO".equalsIgnoreCase(input.source())) {
            limitations.add("DEMO 데이터이므로 실제 투자 판단에 사용할 수 없습니다.");
        } else if ("STALE".equals(input.freshness())) {
            limitations.add("STALE 데이터가 포함되어 최신 시장 상태와 다를 수 있습니다.");
        }

        String serializedInput = input.toString() + promptVersion;
        String outputText = trendText + momentumText + volatilityText + volumeText;
        return new TechnicalExplanationResult(
                "finwatch-demo-technical-v2",
                summary,
                trendText,
                momentumText,
                volatilityText,
                volumeText,
                supporting,
                conflicting,
                List.of("기술지표는 과거 가격 데이터를 계산한 통계값이며 미래의 수익률을 보장하지 않습니다."),
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

    private String getMoney(TechnicalEvidence evidence, String key, String currency) {
        String val = evidence.values().get(key);
        if (val == null) return "—";
        try {
            double d = Double.parseDouble(val);
            return String.format("%,.0f", d) + ("USD".equalsIgnoreCase(currency) ? "$" : "원");
        } catch (Exception e) {
            return val;
        }
    }

    private String getPercent(TechnicalEvidence evidence, String key) {
        String val = evidence.values().get(key);
        if (val == null) return "0.00";
        try {
            double d = Double.parseDouble(val);
            return String.format("%.2f", d);
        } catch (Exception e) {
            return val;
        }
    }

    private String getNumber(TechnicalEvidence evidence, String key) {
        String val = evidence.values().get(key);
        if (val == null) return "0";
        try {
            double d = Double.parseDouble(val);
            return String.format("%,.0f", d);
        } catch (Exception e) {
            return val;
        }
    }

    private String getFloat(TechnicalEvidence evidence, String key) {
        String val = evidence.values().get(key);
        if (val == null) return "0.0";
        try {
            double d = Double.parseDouble(val);
            return String.format("%,.1f", d);
        } catch (Exception e) {
            return val;
        }
    }
}
