package com.finwatch.data.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.data.provider.ProviderException;

@RestControllerAdvice(assignableTypes = ProviderDiagnosticController.class)
public class ProviderExceptionHandler {

    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ApiErrorResponse> handle(ProviderException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
