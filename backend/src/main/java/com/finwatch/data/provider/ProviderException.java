package com.finwatch.data.provider;

import org.springframework.http.HttpStatus;

public class ProviderException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ProviderException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public ProviderException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
