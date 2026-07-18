package com.finwatch.ai.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.finwatch.ai.dto.AiSummaryRequest;
import com.finwatch.ai.dto.AiSummaryResponse;
import com.finwatch.news.content.NewsContentException;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.news.service.NewsContentIngestionService;

@Service
public class AiNewsSummaryOrchestrationService {

    private final NewsArticleRepository newsArticleRepository;
    private final NewsContentIngestionService contentIngestionService;
    private final AiNewsSummaryService newsSummaryService;

    public AiNewsSummaryOrchestrationService(
            NewsArticleRepository newsArticleRepository,
            NewsContentIngestionService contentIngestionService,
            AiNewsSummaryService newsSummaryService) {
        this.newsArticleRepository = newsArticleRepository;
        this.contentIngestionService = contentIngestionService;
        this.newsSummaryService = newsSummaryService;
    }

    public AiSummaryResponse summarize(AiSummaryRequest request) {
        NewsArticle news = newsArticleRepository.findById(request.newsId())
                .orElseThrow(() -> new NewsContentException(
                        HttpStatus.NOT_FOUND,
                        "NEWS_NOT_FOUND",
                        "뉴스를 찾을 수 없습니다."));
        if (!"NEWS".equals(news.getContentKind())) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "NEWS_REQUIRED",
                    "일반 뉴스만 뉴스 요약 API에서 분석할 수 있습니다.");
        }
        if (!hasAnalyzableContent(news)) {
            contentIngestionService.refreshForAiSummary(news.getId());
        }
        return newsSummaryService.summarize(request);
    }

    private boolean hasAnalyzableContent(NewsArticle news) {
        return news.isAiAnalysisAllowed()
                && news.getContentHash() != null
                && !news.getContentHash().isBlank();
    }
}
