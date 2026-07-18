package com.finwatch.auth.kakao;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class KakaoReturnToValidator {

    private static final List<String> ALLOWED_ROUTES = List.of(
            "/dashboard",
            "/stocks",
            "/news",
            "/disclosures",
            "/watchlist",
            "/portfolio",
            "/alerts");

    public String validate(String value) {
        if (value == null || value.isBlank()) {
            return "/dashboard";
        }
        String route = value.trim();
        if (!route.startsWith("/")
                || route.startsWith("//")
                || route.contains("\\")
                || route.chars().anyMatch(Character::isISOControl)
                || route.startsWith("/admin")
                || route.startsWith("/login")) {
            return "/dashboard";
        }
        boolean allowed = ALLOWED_ROUTES.stream()
                .anyMatch(prefix -> route.equals(prefix)
                        || route.startsWith(prefix + "/")
                        || route.startsWith(prefix + "?"));
        return allowed ? route : "/dashboard";
    }
}
