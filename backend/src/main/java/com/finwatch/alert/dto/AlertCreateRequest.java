package com.finwatch.alert.dto;

import java.math.BigDecimal;

import com.finwatch.alert.domain.AlertCondition;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AlertCreateRequest(
        @NotBlank @Size(max = 30) String symbol,
        @NotNull AlertCondition condition,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 4) BigDecimal targetPrice,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) {
}
