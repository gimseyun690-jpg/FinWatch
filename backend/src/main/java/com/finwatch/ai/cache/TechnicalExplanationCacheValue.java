package com.finwatch.ai.cache;

import java.io.Serializable;

import com.finwatch.ai.dto.TechnicalExplanationResponse;

public record TechnicalExplanationCacheValue(
        TechnicalExplanationResponse originalResponse) implements Serializable {
}
