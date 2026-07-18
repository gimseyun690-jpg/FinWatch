package com.finwatch.ai.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.ai.provider.AiProviderException;
import com.finwatch.news.content.NewsContentException;

@RestControllerAdvice(assignableTypes = {
        AiNewsSummaryController.class,
        AiDisclosureSummaryController.class
})
public class AiNewsSummaryExceptionHandler {

    @ExceptionHandler(NewsContentException.class)
    public ResponseEntity<ApiErrorResponse> handleNewsContentException(NewsContentException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleAiProviderException(AiProviderException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
