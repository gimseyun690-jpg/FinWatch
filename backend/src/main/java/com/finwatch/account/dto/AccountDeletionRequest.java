package com.finwatch.account.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountDeletionRequest(
        @NotBlank String confirmation,
        String password) {
}
