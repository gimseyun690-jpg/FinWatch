package com.finwatch.ai.provider;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.finwatch.ai.dto.PortfolioEvaluationInput;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationResult;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationStatement;

@Component
public class PortfolioEvaluationResponseValidator {

    private static final List<String> PROHIBITED_PHRASES = List.of(
            "매수하세요", "매도하세요", "매수 권고", "매도 권고", "매수해야", "매도해야",
            "사야 합니다", "팔아야 합니다", "목표주가", "적정주가", "종목 추천", "추천 종목",
            "수익 보장", "수익을 보장", "반드시 상승", "반드시 하락",
            "상승 확률", "하락 확률", "가격 예측");
    private static final List<Pattern> PROHIBITED_ADVICE_PATTERNS = List.of(
            Pattern.compile(
                    "(?:비중|보유량)(?:을|를)?\\s*"
                            + "(?:늘리|줄이|높이|낮추|확대|축소|증가|감소)"
                            + "[^.!?\\n]{0,30}(?:고려|권장|권고|추천|좋|유리|필요|바람직)"),
            Pattern.compile(
                    "(?:신규\\s*)?편입(?:을|를|하는\\s*것을)?"
                            + "[^.!?\\n]{0,30}(?:고려|권장|권고|추천|좋|유리|필요|바람직)"),
            Pattern.compile(
                    "(?:보유|포지션)(?:을|를)?\\s*(?:유지|정리|청산|처분)"
                            + "[^.!?\\n]{0,30}(?:고려|권장|권고|추천|좋|유리|필요|바람직)"),
            Pattern.compile("(?:추가|분할)\\s*매수|분할\\s*매도|차익\\s*실현|손절"),
            Pattern.compile("\\b(?:buy|sell|hold|overweight|underweight)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    private static final List<Pattern> NON_ADVICE_DISCLAIMERS = List.of(
            Pattern.compile("매수.{0,12}매도\\s*(?:권유|추천)(?:가|이)?\\s*아닌"),
            Pattern.compile("투자\\s*(?:권유|추천)(?:가|이)?\\s*아닌"),
            Pattern.compile(
                    "not\\s+(?:an?\\s+)?(?:buy|sell|hold)(?:.{0,30})(?:recommendation|advice)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![A-Za-z])[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?%?");

    public PortfolioEvaluationResult validate(
            PortfolioEvaluationResult result,
            PortfolioEvaluationInput input) {
        if (result == null) throw AiProviderException.invalid("포트폴리오 AI 응답이 비어 있습니다.");
        requireText(result.modelName(), 100, "modelName");
        requireText(result.headline(), 180, "headline");
        requireText(result.summary(), 1_200, "summary");
        validateStatement(result.diversification(), input, "diversification");
        validateStatement(result.concentration(), input, "concentration");
        validateStatement(result.currencyExposure(), input, "currencyExposure");
        validateStatement(result.performanceContext(), input, "performanceContext");
        validateStatements(result.strengths(), 0, 5, input, "strengths");
        validateStatements(result.riskFactors(), 1, 5, input, "riskFactors");
        validateStatements(result.reviewPoints(), 1, 5, input, "reviewPoints");
        validateStrings(result.dataLimitations(), 0, 8, 350, "dataLimitations");
        if (result.inputTokens() < 0 || result.outputTokens() < 0) {
            throw AiProviderException.invalid("토큰 수는 0 이상이어야 합니다.");
        }

        String statementText = Stream.of(
                        Stream.of(
                                result.diversification(),
                                result.concentration(),
                                result.currencyExposure(),
                                result.performanceContext()),
                        result.strengths().stream(),
                        result.riskFactors().stream(),
                        result.reviewPoints().stream())
                .flatMap(value -> value)
                .map(PortfolioEvaluationStatement::text)
                .reduce("", (first, second) -> first + " " + second);
        String allText = String.join(" ",
                result.headline(),
                result.summary(),
                statementText,
                String.join(" ", result.dataLimitations()));
        String normalized = removeNonAdviceDisclaimers(allText).toLowerCase(Locale.ROOT);
        for (String prohibited : PROHIBITED_PHRASES) {
            if (normalized.contains(prohibited.toLowerCase(Locale.ROOT))) {
                throw AiProviderException.invalid("종목 거래 권고 또는 가격 예측으로 해석될 수 있는 출력이 포함되었습니다.");
            }
        }
        for (Pattern pattern : PROHIBITED_ADVICE_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                throw AiProviderException.invalid("종목 거래 권고 또는 가격 예측으로 해석될 수 있는 출력이 포함되었습니다.");
            }
        }
        Set<String> allEvidenceIds = input.evidence().stream()
                .map(PortfolioEvaluationInput.Evidence::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> generalNumbers = evidenceNumbers(input, allEvidenceIds);
        collectNumbers(input.serverDataLimitations(), generalNumbers);
        validateNumbers(result.headline() + " " + result.summary(), generalNumbers);
        validateNumbers(String.join(" ", result.dataLimitations()), generalNumbers);
        return result;
    }

    private void validateStatements(
            List<PortfolioEvaluationStatement> statements,
            int min,
            int max,
            PortfolioEvaluationInput input,
            String field) {
        if (statements == null || statements.size() < min || statements.size() > max) {
            throw AiProviderException.invalid(field + " 항목 수가 계약과 다릅니다.");
        }
        for (PortfolioEvaluationStatement statement : statements) {
            validateStatement(statement, input, field);
        }
    }

    private void validateStatement(
            PortfolioEvaluationStatement statement,
            PortfolioEvaluationInput input,
            String field) {
        if (statement == null) throw AiProviderException.invalid(field + " 항목이 비어 있습니다.");
        requireText(statement.text(), 500, field + ".text");
        if (statement.evidenceIds() == null
                || statement.evidenceIds().isEmpty()
                || statement.evidenceIds().size() > 5) {
            throw AiProviderException.invalid(field + " 근거 수가 계약과 다릅니다.");
        }
        Set<String> ids = new HashSet<>(statement.evidenceIds());
        Set<String> allowed = input.evidence().stream()
                .map(PortfolioEvaluationInput.Evidence::id)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.size() != statement.evidenceIds().size() || !allowed.containsAll(ids)) {
            throw AiProviderException.invalid(field + "에 존재하지 않거나 중복된 근거 ID가 있습니다.");
        }
        validateNumbers(statement.text(), evidenceNumbers(input, ids));
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

    private Set<String> evidenceNumbers(
            PortfolioEvaluationInput input,
            Set<String> evidenceIds) {
        Set<String> allowed = new HashSet<>();
        for (var evidence : input.evidence()) {
            if (!evidenceIds.contains(evidence.id())) continue;
            collectNumbers(evidence.displayValue(), allowed);
            evidence.values().values().forEach(value -> collectNumbers(value, allowed));
        }
        return allowed;
    }

    private void validateNumbers(String output, Set<String> allowed) {
        Matcher matcher = NUMBER.matcher(output);
        while (matcher.find()) {
            String value = normalizeNumber(matcher.group());
            if (!allowed.contains(value)) {
                throw AiProviderException.invalid("입력 근거에서 추적할 수 없는 숫자가 포함되었습니다.");
            }
        }
    }

    private String removeNonAdviceDisclaimers(String value) {
        String result = value;
        for (Pattern pattern : NON_ADVICE_DISCLAIMERS) {
            result = pattern.matcher(result).replaceAll(" ");
        }
        return result;
    }

    private void collectNumbers(List<String> values, Set<String> target) {
        if (values == null) return;
        values.forEach(value -> collectNumbers(value, target));
    }

    private void collectNumbers(String text, Set<String> target) {
        if (text == null) return;
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            target.add(normalizeNumber(matcher.group()));
        }
    }

    private String normalizeNumber(String value) {
        String cleaned = value.replace(",", "").replace("%", "").replace("+", "");
        try {
            return new BigDecimal(cleaned).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return cleaned;
        }
    }
}
