package com.finwatch.ai.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.finwatch.ai.dto.AiDisclosureSummaryRequest;
import com.finwatch.ai.dto.AiSummaryRequest;
import com.finwatch.ai.dto.AiSummaryResponse;
import com.finwatch.news.content.NewsContentException;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.news.service.NewsContentIngestionService;

@Service
public class AiDisclosureSummaryService {

    private final NewsArticleRepository newsArticleRepository;
    private final NewsContentIngestionService contentIngestionService;
    private final AiNewsSummaryService newsSummaryService;

    public AiDisclosureSummaryService(
            NewsArticleRepository newsArticleRepository,
            NewsContentIngestionService contentIngestionService,
            AiNewsSummaryService newsSummaryService) {
        this.newsArticleRepository = newsArticleRepository;
        this.contentIngestionService = contentIngestionService;
        this.newsSummaryService = newsSummaryService;
    }

    public AiSummaryResponse summarize(AiDisclosureSummaryRequest request) {
        NewsArticle disclosure = newsArticleRepository.findById(request.disclosureId())
                .orElseThrow(() -> new NewsContentException(
                        HttpStatus.NOT_FOUND,
                        "DISCLOSURE_NOT_FOUND",
                        "공시를 찾을 수 없습니다."));
        if (!"DISCLOSURE".equals(disclosure.getContentKind())) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DISCLOSURE_REQUIRED",
                    "공시 콘텐츠만 공시 요약 API에서 분석할 수 있습니다.");
        }
        if (!hasAnalyzableContent(disclosure)) {
            contentIngestionService.refresh(disclosure.getId());
        }
        return newsSummaryService.summarize(new AiSummaryRequest(
                disclosure.getId(),
                request.promptVersion()));
    }

    private boolean hasAnalyzableContent(NewsArticle disclosure) {
        return disclosure.isAiAnalysisAllowed()
                && disclosure.getContentHash() != null
                && !disclosure.getContentHash().isBlank();
    }
}
