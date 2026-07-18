package com.finwatch.portfolio.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;

public record PortfolioUpdateRequest(
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        @DecimalMin("0") @Digits(integer = 16, fraction = 4) BigDecimal averagePurchasePrice,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 10) BigDecimal averagePurchaseFxRate,
        @Pattern(regexp = "[A-Za-z]{3}") String purchaseFxBaseCurrency,
        @Pattern(regexp = "[A-Za-z]{3}") String purchaseFxQuoteCurrency) {

    @AssertTrue(message = "quantity와 averagePurchasePrice 중 하나 이상이 필요합니다.")
    public boolean isAnyFieldPresent() {
        return quantity != null || averagePurchasePrice != null || averagePurchaseFxRate != null
                || purchaseFxBaseCurrency != null || purchaseFxQuoteCurrency != null;
    }

    @AssertTrue(message = "매수 환율 값과 통화쌍은 모두 함께 입력해야 합니다.")
    public boolean isPurchaseFxComplete() {
        boolean none = averagePurchaseFxRate == null && purchaseFxBaseCurrency == null && purchaseFxQuoteCurrency == null;
        boolean all = averagePurchaseFxRate != null && purchaseFxBaseCurrency != null && purchaseFxQuoteCurrency != null
                && !purchaseFxBaseCurrency.equalsIgnoreCase(purchaseFxQuoteCurrency);
        return none || all;
    }
}
