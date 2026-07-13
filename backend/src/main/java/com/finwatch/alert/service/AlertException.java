package com.finwatch.alert.service;

import org.springframework.http.HttpStatus;

public class AlertException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private AlertException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static AlertException notFound() { return new AlertException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "가격 알림을 찾을 수 없습니다."); }
    public static AlertException duplicated() { return new AlertException(HttpStatus.CONFLICT, "ALERT_DUPLICATED", "같은 조건의 가격 알림이 이미 있습니다."); }
    public static AlertException stockNotFound() { return new AlertException(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "종목을 찾을 수 없습니다."); }
    public static AlertException currencyMismatch() { return new AlertException(HttpStatus.UNPROCESSABLE_ENTITY, "ALERT_CURRENCY_MISMATCH", "종목 통화와 요청 통화가 다릅니다."); }
}
