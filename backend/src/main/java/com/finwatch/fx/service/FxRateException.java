package com.finwatch.fx.service;

import org.springframework.http.HttpStatus;

public class FxRateException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public FxRateException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
