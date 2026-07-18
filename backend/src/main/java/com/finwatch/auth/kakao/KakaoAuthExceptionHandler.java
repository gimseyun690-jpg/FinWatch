package com.finwatch.auth.kakao;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.finwatch.auth.session.SessionUnavailableException;
import com.finwatch.common.api.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = KakaoAuthorizationController.class)
public class KakaoAuthExceptionHandler {

    @ExceptionHandler(KakaoLoginException.class)
    public ResponseEntity<ApiErrorResponse> handleKakao(KakaoLoginException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(SessionUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleSessionUnavailable() {
        return ResponseEntity.status(503)
                .body(ApiErrorResponse.of("SESSION_UNAVAILABLE", "인증 저장소를 사용할 수 없습니다."));
    }
}
