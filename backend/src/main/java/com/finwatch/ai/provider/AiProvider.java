package com.finwatch.ai.provider;

import java.util.List;

public interface AiProvider {

    AiProviderResult summarize(
            String title,
            String preprocessedContent,
            String segmentId,
            String promptVersion);

    record AiProviderResult(
            String modelName,
            String summary,
            List<String> keyPoints,
            List<String> positiveFactors,
            List<String> riskFactors,
            List<String> mentionedCompanies,
            List<String> keywords,
            String sentiment,
            int inputTokens,
            int outputTokens) {
    }
}
