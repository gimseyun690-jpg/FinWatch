package com.finwatch.content.service;

import org.springframework.http.HttpStatus;

public class ContentFeedException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ContentFeedException(HttpStatus status, String code, String message) {
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
