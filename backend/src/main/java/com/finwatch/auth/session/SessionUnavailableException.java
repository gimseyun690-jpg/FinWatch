package com.finwatch.auth.session;

public class SessionUnavailableException extends RuntimeException {

    public SessionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
