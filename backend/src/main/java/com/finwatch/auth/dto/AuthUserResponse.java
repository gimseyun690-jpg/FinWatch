package com.finwatch.auth.dto;

import com.finwatch.user.domain.AppUser;

public record AuthUserResponse(Long id, String email, String role) {

    public static AuthUserResponse from(AppUser user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }
}
