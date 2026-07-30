package com.finwatch.ai.cache;

import java.io.Serializable;

import com.finwatch.ai.dto.PortfolioEvaluationResponse;

public record PortfolioEvaluationCacheValue(
        PortfolioEvaluationResponse originalResponse) implements Serializable {
}
