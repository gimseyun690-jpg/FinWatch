package com.finwatch.ai.provider;

import org.springframework.http.HttpStatus;

public class AiProviderException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AiProviderException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public AiProviderException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static AiProviderException invalid(String message) {
        return new AiProviderException(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_INVALID", message);
    }
}
