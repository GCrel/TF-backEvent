package org.gcrel.tfbackevent.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RequestPasswordChangeRequest(
        @NotBlank
        String currentPassword) {
}
