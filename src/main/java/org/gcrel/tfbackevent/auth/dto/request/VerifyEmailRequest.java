package org.gcrel.tfbackevent.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Code is required")
        @Size (min = 6, max = 6, message = "Code must be 6 characters long")
        @Pattern (regexp = "^[0-9]+$", message = "Code must be numeric")
        String code,

        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        String email) {
}
