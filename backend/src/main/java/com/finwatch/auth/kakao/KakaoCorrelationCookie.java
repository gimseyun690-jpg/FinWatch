package com.finwatch.auth.kakao;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class KakaoCorrelationCookie {

    private final String cookieName;
    private final KakaoLoginProperties properties;
    private final boolean secure;
    private final String sameSite;

    public KakaoCorrelationCookie(
            @Value("${app.auth.kakao.correlation-cookie-name}") String cookieName,
            @Value("${app.auth.session.cookie-secure}") boolean secure,
            @Value("${app.auth.session.cookie-same-site}") String sameSite,
            KakaoLoginProperties properties) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.properties = properties;
    }

    public void write(HttpServletResponse response, String correlation) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(correlation, properties.attemptTtl().toSeconds()).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/v1/auth/kakao")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
