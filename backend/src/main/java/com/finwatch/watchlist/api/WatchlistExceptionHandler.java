package com.finwatch.watchlist.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;
import com.finwatch.watchlist.service.WatchlistException;

@RestControllerAdvice(assignableTypes = WatchlistController.class)
public class WatchlistExceptionHandler {

    @ExceptionHandler(WatchlistException.class)
    public ResponseEntity<ApiErrorResponse> handleWatchlistException(WatchlistException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
