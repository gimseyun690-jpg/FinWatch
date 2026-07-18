package com.finwatch.disclosure.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.disclosure.service.DisclosureQueryException;

@RestControllerAdvice(assignableTypes = DisclosureController.class)
public class DisclosureExceptionHandler {

    @ExceptionHandler(DisclosureQueryException.class)
    public ResponseEntity<ApiErrorResponse> handle(DisclosureQueryException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
