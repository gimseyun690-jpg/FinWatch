package com.finwatch.stock.service;

import org.springframework.http.HttpStatus;

public class StockDataLoadException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public StockDataLoadException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
