package com.finwatch.ai.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.finwatch.ai.dto.DailyChangeBriefingInput;
import com.finwatch.ai.dto.DailyChangeBriefingInput.BriefingEvidence;
import com.finwatch.ai.provider.AiProvider.DailyBriefingResult;
import com.finwatch.ai.provider.AiProvider.DailyBriefingStatement;

class DailyBriefingResponseValidatorTest {
    private final DailyBriefingResponseValidator validator = new DailyBriefingResponseValidator();

    @Test void rejectsUnknownEvidenceAndTradingInstruction() {
        DailyChangeBriefingInput input = new DailyChangeBriefingInput("KRX", "000660", "KRW", LocalDate.now(),
                LocalDate.now().minusDays(1), "AVAILABLE", Instant.now(), "technical-v2-wilder",
                "daily-briefing-input-v1", "DEMO", "DEMO", BigDecimal.ONE, BigDecimal.ONE, "PARTIAL", List.of(),
                List.of(new BriefingEvidence("T1", "TECHNICAL", "PRICE_CHANGE", "1", "0", "1", "종가 변화", Map.of("type", "CHART_INDICATOR"))),
                0, List.of("DEMO 데이터입니다."));
        DailyBriefingResult invalidEvidence = result("근거 요약", List.of("X9"));
        assertThatThrownBy(() -> validator.validate(invalidEvidence, input)).isInstanceOf(AiProviderException.class);
        DailyBriefingResult instruction = result("지금 매수하세요", List.of("T1"));
        assertThatThrownBy(() -> validator.validate(instruction, input)).isInstanceOf(AiProviderException.class);
    }

    private DailyBriefingResult result(String headline, List<String> ids) {
        return new DailyBriefingResult("model", headline, ids, "전일 대비 변화를 정리했습니다.", ids,
                List.of(new DailyBriefingStatement("변화 근거입니다.", ids)), List.of(), List.of(), List.of(), List.of(),
                List.of("DEMO 데이터입니다."), 10, 10);
    }
}
