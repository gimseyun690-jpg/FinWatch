package com.finwatch.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SessionCookieWriter {

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final String domain;

    public SessionCookieWriter(
            @Value("${app.auth.session.cookie-name}") String cookieName,
            @Value("${app.auth.session.cookie-secure}") boolean secure,
            @Value("${app.auth.session.cookie-same-site}") String sameSite,
            @Value("${app.auth.session.cookie-domain:}") String domain) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = domain == null ? "" : domain.trim();
    }

    public void write(HttpServletResponse response, String rawSessionId, Instant expiresAt) {
        long seconds = Math.max(0, Duration.between(Instant.now(), expiresAt).toSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(rawSessionId, Duration.ofSeconds(seconds)).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
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

    public String cookieName() {
        return cookieName;
    }

    public boolean secure() {
        return secure;
    }

    public String sameSite() {
        return sameSite;
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge);
        if (!domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }
}
