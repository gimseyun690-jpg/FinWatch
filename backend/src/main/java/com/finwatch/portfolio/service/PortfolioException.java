package com.finwatch.portfolio.service;

import org.springframework.http.HttpStatus;

public class PortfolioException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private PortfolioException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static PortfolioException duplicated() {
        return new PortfolioException(HttpStatus.CONFLICT, "HOLDING_DUPLICATED", "이미 등록된 보유 종목입니다.");
    }

    public static PortfolioException notFound() {
        return new PortfolioException(HttpStatus.NOT_FOUND, "HOLDING_NOT_FOUND", "보유 종목을 찾을 수 없습니다.");
    }

    public static PortfolioException stockNotFound() {
        return new PortfolioException(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "종목을 찾을 수 없습니다.");
    }

    public static PortfolioException currencyMismatch() {
        return new PortfolioException(HttpStatus.UNPROCESSABLE_ENTITY, "HOLDING_CURRENCY_MISMATCH", "종목 통화와 요청 통화가 다릅니다.");
    }
}
