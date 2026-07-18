package com.finwatch.ai.service;

import org.springframework.http.HttpStatus;

public class TechnicalExplanationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TechnicalExplanationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public TechnicalExplanationException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
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
