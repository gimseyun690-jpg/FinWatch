package com.finwatch.data.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.data.sync.DataSyncException;

@RestControllerAdvice(assignableTypes = AdminDataSyncController.class)
public class DataSyncExceptionHandler {

    @ExceptionHandler(DataSyncException.class)
    public ResponseEntity<ApiErrorResponse> handle(DataSyncException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
