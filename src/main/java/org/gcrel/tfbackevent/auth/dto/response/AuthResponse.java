package org.gcrel.tfbackevent.auth.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken) {
}
