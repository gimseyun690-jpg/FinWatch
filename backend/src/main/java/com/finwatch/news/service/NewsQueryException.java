package com.finwatch.news.service;

import org.springframework.http.HttpStatus;

public class NewsQueryException extends RuntimeException {
    private final HttpStatus status; private final String code;
    public NewsQueryException(HttpStatus status, String code, String message) { super(message); this.status = status; this.code = code; }
    public HttpStatus getStatus() { return status; } public String getCode() { return code; }
}
