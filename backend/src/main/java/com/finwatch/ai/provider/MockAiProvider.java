package com.finwatch.ai.provider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
