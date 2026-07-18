package com.finwatch.ai.provider;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.finwatch.ai.dto.DailyChangeBriefingInput;
import com.finwatch.ai.provider.AiProvider.DailyBriefingResult;
import com.finwatch.ai.provider.AiProvider.DailyBriefingStatement;

@Component
public class DailyBriefingResponseValidator {
    private static final List<String> PROHIBITED = List.of("목표주가", "적정주가", "매수하세요", "매도하세요",
            "수익 보장", "반드시 상승", "반드시 하락", "상승 확률", "하락 확률", "손절가", "진입가");

    public DailyBriefingResult validate(DailyBriefingResult result, DailyChangeBriefingInput input) {
        if (result == null) throw AiProviderException.invalid("일일 브리핑 응답이 비어 있습니다.");
        text(result.modelName(), 100, "modelName"); text(result.headline(), 120, "headline");
        text(result.changeSummary(), 1200, "changeSummary");
        Set<String> allowed = input.evidence().stream().map(DailyChangeBriefingInput.BriefingEvidence::id)
                .collect(java.util.stream.Collectors.toSet());
        ids(result.headlineEvidenceIds(), allowed, "headlineEvidenceIds");
        ids(result.changeSummaryEvidenceIds(), allowed, "changeSummaryEvidenceIds");
        statements(result.newStrengths(), allowed, "newStrengths"); statements(result.newRisks(), allowed, "newRisks");
        statements(result.unchangedContext(), allowed, "unchangedContext"); statements(result.alignedViews(), allowed, "alignedViews");
        statements(result.conflictingViews(), allowed, "conflictingViews");
        if (result.dataLimitations() == null || result.dataLimitations().size() > 5) throw AiProviderException.invalid("dataLimitations 항목 수가 잘못되었습니다.");
        result.dataLimitations().forEach(value -> text(value, 300, "dataLimitations"));
        String all = Stream.of(result.headline(), result.changeSummary(),
                        join(result.newStrengths()), join(result.newRisks()), join(result.unchangedContext()),
                        join(result.alignedViews()), join(result.conflictingViews()), String.join(" ", result.dataLimitations()))
                .collect(java.util.stream.Collectors.joining(" ")).toLowerCase(Locale.ROOT);
        for (String phrase : PROHIBITED) if (all.contains(phrase.toLowerCase(Locale.ROOT)))
            throw AiProviderException.invalid("투자 권유·예측 금지 문구가 포함되었습니다.");
        if (result.inputTokens() < 0 || result.outputTokens() < 0) throw AiProviderException.invalid("토큰 수는 0 이상이어야 합니다.");
        return result;
    }

    private void statements(List<DailyBriefingStatement> values, Set<String> allowed, String field) {
        if (values == null || values.size() > 5) throw AiProviderException.invalid(field + " 항목 수가 잘못되었습니다.");
        for (DailyBriefingStatement value : values) { if (value == null) throw AiProviderException.invalid(field + " 항목이 비어 있습니다."); text(value.text(), 350, field); ids(value.evidenceIds(), allowed, field); }
    }
    private void ids(List<String> values, Set<String> allowed, String field) { if (values == null || values.isEmpty() || values.size() > 6 || new HashSet<>(values).size() != values.size() || !allowed.containsAll(values)) throw AiProviderException.invalid(field + " 근거 ID가 잘못되었습니다."); }
    private void text(String value, int max, String field) { if (value == null || value.isBlank() || value.trim().length() > max) throw AiProviderException.invalid(field + " 값이 계약과 다릅니다."); }
    private String join(List<DailyBriefingStatement> values) { return values == null ? "" : values.stream().map(DailyBriefingStatement::text).collect(java.util.stream.Collectors.joining(" ")); }
}
