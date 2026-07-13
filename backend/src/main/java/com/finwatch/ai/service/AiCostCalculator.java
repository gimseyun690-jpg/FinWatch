package com.finwatch.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiCostCalculator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final BigDecimal inputPricePerMillion;
    private final BigDecimal outputPricePerMillion;

    public AiCostCalculator(
            @Value("${app.ai.input-price-per-million}") BigDecimal inputPricePerMillion,
            @Value("${app.ai.output-price-per-million}") BigDecimal outputPricePerMillion) {
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
    }

    public BigDecimal calculate(int inputTokens, int outputTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .multiply(inputPricePerMillion)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .multiply(outputPricePerMillion)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP);
    }
}

