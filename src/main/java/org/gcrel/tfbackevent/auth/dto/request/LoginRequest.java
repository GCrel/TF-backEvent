package org.gcrel.tfbackevent.auth.dto.request;

public record LoginRequest(
        String email,
        String password) {
}
