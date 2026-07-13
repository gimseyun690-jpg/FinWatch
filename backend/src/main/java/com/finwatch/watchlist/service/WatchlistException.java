package com.finwatch.watchlist.service;

import org.springframework.http.HttpStatus;

public class WatchlistException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private WatchlistException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static WatchlistException duplicated() {
        return new WatchlistException(
                HttpStatus.CONFLICT,
                "WATCHLIST_DUPLICATED",
                "이미 등록된 관심종목입니다.");
    }

    public static WatchlistException notFound() {
        return new WatchlistException(
                HttpStatus.NOT_FOUND,
                "WATCHLIST_NOT_FOUND",
                "관심종목을 찾을 수 없습니다.");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
