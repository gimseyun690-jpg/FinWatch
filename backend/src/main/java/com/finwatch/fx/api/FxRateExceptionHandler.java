package com.finwatch.fx.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.fx.service.FxRateException;

@RestControllerAdvice(assignableTypes = FxRateController.class)
public class FxRateExceptionHandler {
    @ExceptionHandler(FxRateException.class)
    public ResponseEntity<ApiErrorResponse> handle(FxRateException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
