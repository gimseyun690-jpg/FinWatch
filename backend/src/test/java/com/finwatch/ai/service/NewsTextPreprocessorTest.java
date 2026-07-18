package com.finwatch.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewsTextPreprocessorTest {

    private final NewsTextPreprocessor preprocessor = new NewsTextPreprocessor();

    @Test
    void removesHtmlAdsUrlsAndDuplicateSentences() {
        String content = "<p>HBM 수요가 증가했다.</p> 광고 문의는 제외합니다. "
                + "HBM 수요가 증가했다. https://example.com 원문입니다.";

        String result = preprocessor.preprocess(content);

        assertThat(result).doesNotContain("<p>", "광고", "https://");
        assertThat(result.split("HBM 수요가 증가했다", -1)).hasSize(2);
    }
}

