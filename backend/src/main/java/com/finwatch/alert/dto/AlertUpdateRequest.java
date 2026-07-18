package com.finwatch.alert.dto;

import java.math.BigDecimal;

import com.finwatch.alert.domain.AlertCondition;
import com.finwatch.alert.domain.AlertStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record AlertUpdateRequest(
        AlertCondition condition,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 4) BigDecimal targetPrice,
        AlertStatus status) {

    @AssertTrue(message = "변경할 알림 필드가 필요합니다.")
    public boolean isAnyFieldPresent() {
        return condition != null || targetPrice != null || status != null;
    }

    @AssertTrue(message = "TRIGGERED 상태는 서버 평가로만 설정할 수 있습니다.")
    public boolean isWritableStatus() {
        return status != AlertStatus.TRIGGERED;
    }
}
