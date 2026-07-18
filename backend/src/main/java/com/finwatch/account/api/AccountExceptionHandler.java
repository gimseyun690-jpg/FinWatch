package com.finwatch.account.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.common.api.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = AccountController.class)
public class AccountExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleReauthentication() {
        return ApiErrorResponse.of("REAUTHENTICATION_REQUIRED", "계정 삭제를 위해 다시 인증해 주세요.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConfirmation(IllegalArgumentException exception) {
        return ApiErrorResponse.of("ACCOUNT_DELETE_CONFIRMATION_INVALID", exception.getMessage());
    }
}
