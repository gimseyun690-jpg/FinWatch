package com.finwatch.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        List<Object> fieldErrors,
        Instant timestamp) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, code, message, List.of(), Instant.now());
    }
}
