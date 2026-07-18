package com.finwatch.news.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.news.service.NewsQueryException;

@RestControllerAdvice(assignableTypes = NewsController.class)
public class NewsQueryExceptionHandler {
    @ExceptionHandler(NewsQueryException.class)
    public ResponseEntity<ApiErrorResponse> handle(NewsQueryException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
