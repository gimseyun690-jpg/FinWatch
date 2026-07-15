package com.finwatch.ai.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.ai.provider.AiProviderException;
import com.finwatch.ai.service.TechnicalExplanationException;
import com.finwatch.common.api.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = AiTechnicalExplanationController.class)
public class AiTechnicalExplanationExceptionHandler {

    @ExceptionHandler(TechnicalExplanationException.class)
    public ResponseEntity<ApiErrorResponse> handleTechnicalException(TechnicalExplanationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderException(AiProviderException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
