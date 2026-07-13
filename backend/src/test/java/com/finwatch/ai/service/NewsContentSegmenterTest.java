package com.finwatch.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewsContentSegmenterTest {

    @Test
    void splitsLongContentIntoAddressableEvidenceSegments() {
        NewsContentSegmenter segmenter = new NewsContentSegmenter(300, 3);
        String content = "가".repeat(320) + ". " + "나".repeat(320) + ".";

        var result = segmenter.segment(content);

        assertThat(result.segments()).hasSize(3);
        assertThat(result.segments()).extracting(NewsContentSegmenter.ContentSegment::id)
                .containsExactly("S1", "S2", "S3");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void keepsShortContentInOneSegment() {
        NewsContentSegmenter segmenter = new NewsContentSegmenter(1_000, 4);

        var result = segmenter.segment("첫 문장입니다. 두 번째 문장입니다.");

        assertThat(result.segments()).hasSize(1);
        assertThat(result.segments().getFirst().content()).contains("첫 문장", "두 번째 문장");
        assertThat(result.truncated()).isFalse();
    }
}
