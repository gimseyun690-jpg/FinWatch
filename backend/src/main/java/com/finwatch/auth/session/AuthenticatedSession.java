package com.finwatch.auth.session;

import java.time.Instant;

import com.finwatch.user.domain.AppUser;
import com.finwatch.user.identity.AuthProvider;

public record AuthenticatedSession(
        String sessionHash,
        AppUser user,
        AuthProvider provider,
        Instant createdAt,
        Instant expiresAt) {
}
