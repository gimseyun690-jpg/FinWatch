package com.finwatch.stock.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.stock.service.StockQueryException;
import com.finwatch.stock.service.StockDataLoadException;

@RestControllerAdvice(assignableTypes = StockController.class)
public class StockExceptionHandler {

    @ExceptionHandler(StockQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleStockQueryException(StockQueryException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(StockDataLoadException.class)
    public ResponseEntity<ApiErrorResponse> handleStockDataLoadException(StockDataLoadException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
