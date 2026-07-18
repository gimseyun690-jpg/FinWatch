package com.finwatch.news.content;

import org.springframework.http.HttpStatus;

public class NewsContentException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public NewsContentException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public NewsContentException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static NewsContentException unavailable() {
        return new NewsContentException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "NEWS_CONTENT_UNAVAILABLE",
                "이 출처는 AI 분석에 사용할 수 있는 뉴스 본문을 제공하지 않습니다.");
    }
}
