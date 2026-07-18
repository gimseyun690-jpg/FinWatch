package com.finwatch.auth.dto;

import java.time.Instant;

public record LoginResponse(
        boolean authenticated,
        Instant expiresAt,
        AuthUserResponse user) {
}
