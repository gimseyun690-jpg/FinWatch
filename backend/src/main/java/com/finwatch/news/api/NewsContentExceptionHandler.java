package com.finwatch.news.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.news.content.NewsContentException;

@RestControllerAdvice(assignableTypes = AdminNewsContentController.class)
public class NewsContentExceptionHandler {

    @ExceptionHandler(NewsContentException.class)
    public ResponseEntity<ApiErrorResponse> handleNewsContentException(NewsContentException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
