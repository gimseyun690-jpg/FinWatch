package com.finwatch.ai.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.ai.provider.AiProviderException;
import com.finwatch.ai.service.PortfolioEvaluationException;
import com.finwatch.common.api.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = AiPortfolioEvaluationController.class)
public class AiPortfolioEvaluationExceptionHandler {

    @ExceptionHandler(PortfolioEvaluationException.class)
    public ResponseEntity<ApiErrorResponse> handlePortfolio(
            PortfolioEvaluationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleProvider(
            AiProviderException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
