package com.finwatch.portfolio.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record PortfolioUpdateRequest(
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        @DecimalMin("0") @Digits(integer = 16, fraction = 4) BigDecimal averagePurchasePrice) {

    @AssertTrue(message = "quantity와 averagePurchasePrice 중 하나 이상이 필요합니다.")
    public boolean isAnyFieldPresent() {
        return quantity != null || averagePurchasePrice != null;
    }
}
