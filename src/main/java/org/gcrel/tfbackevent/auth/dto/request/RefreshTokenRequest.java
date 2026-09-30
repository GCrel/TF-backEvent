package org.gcrel.tfbackevent.auth.dto.request;

import org.hibernate.validator.constraints.UUID;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank @UUID(message = "Invalid refresh token format")
        String refreshToken) {
}
