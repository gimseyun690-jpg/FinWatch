package com.finwatch.ai.provider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.finwatch.ai.dto.TechnicalExplanationInput;
import com.finwatch.ai.dto.DailyChangeBriefingInput;
import com.finwatch.ai.dto.PortfolioEvaluationInput;
import com.finwatch.ai.dto.TechnicalExplanationInput.TechnicalEvidence;
import com.finwatch.ai.provider.AiProvider.TechnicalSignalExplanation;
import com.finwatch.ai.provider.AiProvider.DailyBriefingStatement;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationStatement;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    @Override
    public AiProviderResult summarize(
            String title,
            String preprocessedContent,
            String segmentId,
            String promptVersion) {
        boolean isEnglish = isMostlyEnglish(title + " " + preprocessedContent);
        
        String summary;
        List<String> keyPoints;
        
        if (isEnglish) {
            summary = "[해외 뉴스 요약] " + (title != null && !title.isBlank() ? title : "해외 기업") + "의 최신 동향과 관련된 주요 보도입니다. 글로벌 공급망 개선 및 수요 확대 흐름 속에서 신제품 출시와 견조한 실적을 기반으로 긍정적인 시장 신호를 보이고 있으며, 글로벌 기술 경쟁 심화가 장기 변수로 지적되었습니다.";
            keyPoints = List.of(
                "글로벌 시장의 신규 파트너십 구축 및 기술 라이선스 체결 소식이 공유되었습니다.",
                "최근 분기 실적 지표가 원자재 가격 안정화 및 물류 흐름 개선으로 시장 예상을 상회했습니다.",
                "단기적인 인플레이션과 거시경제적 금리 변동 영향에 따른 리스크 요인이 분석되었습니다."
            );
        } else {
            List<String> sentences = List.of(preprocessedContent.split("(?<=[.!?])\\s+"));
            keyPoints = sentences.stream().filter(sentence -> !sentence.isBlank()).limit(3).toList();
            summary = String.join(" ", keyPoints);
            if (summary.isBlank()) {
                summary = title + " 관련 핵심 내용을 확인했습니다.";
            }
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

        String sentiment = containsAny(preprocessedContent, "증가", "확대", "회복", "성장", "상향", "increase", "growth", "positive", "expand")
                ? "POSITIVE"
                : containsAny(preprocessedContent, "감소", "하락", "위험", "둔화", "decrease", "decline", "risk", "drop") ? "NEGATIVE" : "NEUTRAL";
        List<String> positiveFactors = containsAny(preprocessedContent, "증가", "확대", "회복", "성장", "상향", "increase", "growth", "positive", "expand")
                ? List.of(isEnglish ? "글로벌 신규 공급 계약 및 매출 견인 동력이 확인되었습니다." : "수요와 실적 개선 가능성이 언급되었습니다.")
                : List.of();
        List<String> riskFactors = containsAny(preprocessedContent, "위험", "변동", "경쟁", "둔화", "감소", "decrease", "decline", "risk", "drop")
                ? List.of(isEnglish ? "글로벌 금리 기조 및 경쟁사 신공정 진입에 따른 마진 압박 요인이 있습니다." : "시장 변동성과 경쟁 환경을 함께 확인해야 합니다.")
                : List.of(isEnglish ? "시장 점유율 유지 여부와 거시 경제 여건의 변화에 유의해야 합니다." : "기사에 구체적인 위험 요인이 충분히 제시되지 않았습니다.");
        List<String> mentionedCompanies = (title + " " + preprocessedContent).contains("SK하이닉스") || (title + " " + preprocessedContent).toLowerCase().contains("hynix")
                ? List.of("SK하이닉스")
                : (title + " " + preprocessedContent).toLowerCase().contains("apple") ? List.of("애플") : List.of();
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

    private boolean isMostlyEnglish(String text) {
        if (text == null || text.isBlank()) return false;
        long englishChars = text.chars().filter(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')).count();
        return (double) englishChars / text.length() > 0.3;
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

    @Override
    public PortfolioEvaluationResult evaluatePortfolio(
            PortfolioEvaluationInput input,
            String promptVersion) {
        String headline = switch (input.concentrationBand()) {
            case "DIVERSIFIED" -> "여러 보유 종목에 자산이 비교적 고르게 분산되어 있습니다.";
            case "MODERATE_CONCENTRATION" -> "일부 보유 종목의 비중이 포트폴리오 흐름에 영향을 주고 있습니다.";
            case "HIGH_CONCENTRATION" -> "상위 보유 종목의 비중이 전체 평가에 큰 영향을 주는 구조입니다.";
            default -> "가격 또는 환율이 불완전해 전체 자산 구성을 제한적으로 평가했습니다.";
        };
        PortfolioEvaluationStatement diversification = new PortfolioEvaluationStatement(
                input.conversionComplete()
                        ? "서버가 계산한 종목별 기준통화 비중을 바탕으로 분산 상태를 확인했습니다."
                        : "서로 다른 통화를 하나의 전체 비중으로 합치지 않고 데이터 한계를 우선 확인했습니다.",
                List.of("C1"));
        PortfolioEvaluationStatement concentration = new PortfolioEvaluationStatement(
                "최대 보유 종목과 상위 보유 종목 묶음의 비중, 집중도 지표를 함께 살폈습니다.",
                List.of("C1"));
        PortfolioEvaluationStatement currency = new PortfolioEvaluationStatement(
                "통화별 평가 비중을 기준으로 환율 변화에 노출되는 자산 구성을 확인했습니다.",
                List.of("FX1"));
        PortfolioEvaluationStatement performance = new PortfolioEvaluationStatement(
                input.profitLossComplete()
                        ? "현재 평가액과 매입 기준 금액의 차이를 포트폴리오 성과 맥락으로 확인했습니다."
                        : "매수 당시 환율이 부족한 항목은 통합 손익을 확정하지 않고 제한사항으로 분리했습니다.",
                List.of("P1"));
        List<PortfolioEvaluationStatement> strengths = input.conversionComplete()
                ? List.of(new PortfolioEvaluationStatement(
                        "기준통화로 환산된 동일 기준의 평가 비중을 확인할 수 있습니다.",
                        List.of("P1", "FX1")))
                : List.of();
        List<PortfolioEvaluationStatement> risks = List.of(new PortfolioEvaluationStatement(
                "상위 종목 집중도와 통화 노출이 전체 평가액 변동에 미치는 영향을 함께 점검해야 합니다.",
                List.of("C1", "FX1")));
        List<PortfolioEvaluationStatement> reviewPoints = List.of(new PortfolioEvaluationStatement(
                "보유 구성과 환율 기준시각이 바뀔 때 동일한 기준으로 비중 변화를 다시 확인할 수 있습니다.",
                List.of("P1", "C1", "FX1")));
        String summary = headline + " 이 평가는 서버가 산출한 비중과 손익 근거를 설명한 참고 정보입니다.";
        String output = headline + summary + diversification + concentration + currency + performance
                + strengths + risks + reviewPoints;
        return new PortfolioEvaluationResult(
                "finwatch-demo-portfolio-v1",
                headline,
                summary,
                diversification,
                concentration,
                currency,
                performance,
                strengths,
                risks,
                reviewPoints,
                input.serverDataLimitations(),
                estimateTokens(input.toString() + promptVersion),
                estimateTokens(output));
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
        return evidenceValue(evidence, key) + ("USD".equalsIgnoreCase(currency) ? "$" : "원");
    }

    private String getPercent(TechnicalEvidence evidence, String key) {
        return evidenceValue(evidence, key);
    }

    private String getNumber(TechnicalEvidence evidence, String key) {
        return evidenceValue(evidence, key);
    }

    private String getFloat(TechnicalEvidence evidence, String key) {
        return evidenceValue(evidence, key);
    }

    private String evidenceValue(TechnicalEvidence evidence, String key) {
        String value = evidence.values().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(evidence.indicator() + "." + key + " 근거가 없습니다.");
        }
        return value;
    }

    @Override
    public List<Long> selectImportantNews(String stockName, List<NewsItemForSelection> newsItems, int limit) {
        return newsItems.stream()
                .map(NewsItemForSelection::id)
                .limit(limit)
                .toList();
    }
}
