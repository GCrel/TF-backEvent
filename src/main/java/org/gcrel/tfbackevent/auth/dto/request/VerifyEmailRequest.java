package org.gcrel.tfbackevent.auth.dto.request;

public record VerifyEmailRequest(
        String code,
        String email) {
}
