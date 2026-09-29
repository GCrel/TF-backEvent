package org.gcrel.tfbackevent.auth.dto.request;

import org.hibernate.validator.constraints.UUID;

public record RefreshTokenRequest(
        @UUID(message = "Invalid refresh token format")
        String refreshToken) {
}
