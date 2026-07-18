package com.finwatch.alert.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.alert.service.AlertException;
import com.finwatch.common.api.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = AlertController.class)
public class AlertExceptionHandler {
    @ExceptionHandler(AlertException.class)
    public ResponseEntity<ApiErrorResponse> handle(AlertException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
