package com.finwatch.portfolio.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PortfolioCreateRequest(
        @NotBlank @Size(max = 30) String symbol,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 4) BigDecimal averagePurchasePrice,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) {
}
