package com.finwatch.auth.dto;

import com.finwatch.user.domain.AppUser;
import com.finwatch.user.identity.AuthProvider;

public record AuthUserResponse(
        Long id,
        String displayName,
        String email,
        String profileImageUrl,
        String role,
        String authProvider) {

    public static AuthUserResponse from(AppUser user, AuthProvider provider) {
        return new AuthUserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getRole().name(),
                provider.name());
    }
}
