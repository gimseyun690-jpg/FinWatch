package com.finwatch.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewsPublisherNameTest {

    @Test
    void replacesNaverPlaceholderWithOriginalPublisherName() {
        assertThat(NewsPublisherName.resolve(
                "Naver News",
                "https://www.ajunews.com/view/20260715220000027"))
                .isEqualTo("아주경제");
    }

    @Test
    void keepsPublisherProvidedByNewsApi() {
        assertThat(NewsPublisherName.resolve(
                "Reuters",
                "https://www.reuters.com/technology/story"))
                .isEqualTo("Reuters");
    }

    @Test
    void fallsBackToOriginalArticleDomain() {
        assertThat(NewsPublisherName.resolve(
                "NAVER_API_HUB",
                "https://news.unknown-example.com/article/1"))
                .isEqualTo("news.unknown-example.com");
    }

    @Test
    void resolvesPublishersObservedInLiveNaverNews() {
        assertThat(NewsPublisherName.resolve("Naver News", "https://biz.sbs.co.kr/article/1"))
                .isEqualTo("SBS Biz");
        assertThat(NewsPublisherName.resolve("NAVER_API_HUB", "https://www.yonhapnewstv.co.kr/news/1"))
                .isEqualTo("연합뉴스TV");
        assertThat(NewsPublisherName.resolve(null, "https://www.dailian.co.kr/news/view/1"))
                .isEqualTo("데일리안");
    }
}
