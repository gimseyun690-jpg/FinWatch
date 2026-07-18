package com.finwatch.data.sync;

import org.springframework.http.HttpStatus;

public class DataSyncException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DataSyncException(HttpStatus status, String code, String message) {
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
