package com.finwatch.content.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.content.service.ContentFeedException;

@RestControllerAdvice(assignableTypes = ContentFeedController.class)
public class ContentFeedExceptionHandler {

    @ExceptionHandler(ContentFeedException.class)
    public ResponseEntity<ApiErrorResponse> handle(ContentFeedException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
