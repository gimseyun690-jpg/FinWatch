package com.finwatch.auth.kakao;

import org.springframework.http.HttpStatus;

public class KakaoLoginException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public KakaoLoginException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public KakaoLoginException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
