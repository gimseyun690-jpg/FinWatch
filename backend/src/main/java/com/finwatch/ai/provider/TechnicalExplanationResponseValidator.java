package com.finwatch.ai.provider;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.finwatch.ai.dto.TechnicalExplanationInput;
import com.finwatch.ai.provider.AiProvider.TechnicalExplanationResult;
import com.finwatch.ai.provider.AiProvider.TechnicalSignalExplanation;

@Component
public class TechnicalExplanationResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(TechnicalExplanationResponseValidator.class);
    private static final List<String> PROHIBITED_PHRASES = List.of(
            "목표주가", "적정주가", "매수하세요", "매도하세요", "반드시 상승", "반드시 하락",
            "수익을 보장합니다", "수익 보장", "수익률은", "상승 확률", "하락 확률");
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z])[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?%?");

    public TechnicalExplanationResult validate(
            TechnicalExplanationResult result,
            TechnicalExplanationInput input) {
        if (result == null) throw AiProviderException.invalid("AI 공급자 응답이 비어 있습니다.");
        requireText(result.modelName(), 100, "modelName");
        requireText(result.summary(), 1_000, "summary");
        requireText(result.trendExplanation(), 500, "trendExplanation");
        requireText(result.momentumExplanation(), 500, "momentumExplanation");
        requireText(result.volatilityExplanation(), 500, "volatilityExplanation");
        requireText(result.volumeExplanation(), 500, "volumeExplanation");
        validateSignals(result.supportingSignals(), 1, 5, input, "supportingSignals");
        validateSignals(result.conflictingSignals(), 0, 5, input, "conflictingSignals");
        validateStrings(result.riskNotes(), 1, 5, 300, "riskNotes");
        validateStrings(result.dataLimitations(), 0, 5, 300, "dataLimitations");
        if (result.inputTokens() < 0 || result.outputTokens() < 0) {
            throw AiProviderException.invalid("토큰 수는 0 이상이어야 합니다.");
        }

        String signalText = java.util.stream.Stream.concat(
                        result.supportingSignals().stream(),
                        result.conflictingSignals().stream())
                .map(TechnicalSignalExplanation::text)
                .collect(java.util.stream.Collectors.joining(" "));
        String allText = String.join(" ", List.of(
                result.summary(), result.trendExplanation(), result.momentumExplanation(),
                result.volatilityExplanation(), result.volumeExplanation(),
                signalText, String.join(" ", result.riskNotes()), String.join(" ", result.dataLimitations())));
        String normalized = allText.toLowerCase(Locale.ROOT);
        for (String prohibited : PROHIBITED_PHRASES) {
            if (normalized.contains(prohibited.toLowerCase(Locale.ROOT))) {
                throw AiProviderException.invalid("투자 권유 또는 예측으로 해석될 수 있는 출력이 포함되었습니다.");
            }
        }
        if (("DEMO".equals(input.freshness()) || "DEMO".equalsIgnoreCase(input.source()))
                && result.dataLimitations().stream().noneMatch(this::mentionsDemo)) {
            throw AiProviderException.invalid("DEMO 데이터 한계가 누락되었습니다.");
        }
        if ("STALE".equals(input.freshness())
                && result.dataLimitations().stream().noneMatch(value -> value.toUpperCase(Locale.ROOT).contains("STALE")
                        || value.contains("지연") || value.contains("오래"))) {
            throw AiProviderException.invalid("STALE 데이터 한계가 누락되었습니다.");
        }
        validateNumbers(allText, input);
        return result;
    }

    private void validateNumbers(String output, TechnicalExplanationInput input) {
        Set<String> allowed = new HashSet<>(List.of("1", "2", "5", "14", "20", "30", "60", "70"));
        allowed.add(Integer.toString(input.sampleCount()));
        collectNumbers(input.symbol(), allowed);
        collectNumbers(input.interval(), allowed);
        collectNumbers(input.latestRecordedAt().toString(), allowed);
        collectNumbers(input.calculationVersion(), allowed);
        for (var evidence : input.evidence()) {
            collectNumbers(evidence.displayValue(), allowed);
            evidence.values().values().forEach(value -> collectNumbers(value, allowed));
        }
        Matcher matcher = NUMBER.matcher(output);
        while (matcher.find()) {
            String normalized = normalizeNumber(matcher.group());
            if (!allowed.contains(normalized)) {
                log.warn("Rejected technical explanation number absent from server evidence: {}", normalized);
                throw AiProviderException.invalid("입력 근거에서 추적할 수 없는 숫자가 포함되었습니다.");
            }
        }
    }

    private void collectNumbers(String text, Set<String> target) {
        if (text == null) return;
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) target.add(normalizeNumber(matcher.group()));
    }

    private String normalizeNumber(String value) {
        String cleaned = value.replace(",", "").replace("%", "").replace("+", "");
        try {
            return new java.math.BigDecimal(cleaned).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return cleaned;
        }
    }

    private void validateSignals(
            List<TechnicalSignalExplanation> signals,
            int min,
            int max,
            TechnicalExplanationInput input,
            String field) {
        if (signals == null || signals.size() < min || signals.size() > max) {
            throw AiProviderException.invalid(field + " 항목 수가 계약과 다릅니다.");
        }
        Set<String> allowedIds = input.evidence().stream()
                .map(TechnicalExplanationInput.TechnicalEvidence::id)
                .collect(java.util.stream.Collectors.toSet());
        for (TechnicalSignalExplanation signal : signals) {
            if (signal == null) throw AiProviderException.invalid(field + " 항목이 비어 있습니다.");
            requireText(signal.text(), 300, field + ".text");
            if (signal.evidenceIds() == null || signal.evidenceIds().isEmpty() || signal.evidenceIds().size() > 4) {
                throw AiProviderException.invalid(field + " 근거 수가 계약과 다릅니다.");
            }
            if (new HashSet<>(signal.evidenceIds()).size() != signal.evidenceIds().size()
                    || !allowedIds.containsAll(signal.evidenceIds())) {
                throw AiProviderException.invalid(field + "에 존재하지 않거나 중복된 근거 ID가 있습니다.");
            }
        }
    }

    private void validateStrings(List<String> values, int min, int max, int itemMax, String field) {
        if (values == null || values.size() < min || values.size() > max) {
            throw AiProviderException.invalid(field + " 항목 수가 계약과 다릅니다.");
        }
        for (String value : values) requireText(value, itemMax, field);
    }

    private void requireText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw AiProviderException.invalid(field + " 값이 계약과 다릅니다.");
        }
    }

    private boolean mentionsDemo(String value) {
        return value != null && (value.toUpperCase(Locale.ROOT).contains("DEMO") || value.contains("데모"));
    }
}
