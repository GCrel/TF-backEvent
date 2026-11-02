package org.gcrel.tfbackevent.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestEmailChangeRequest(
        @NotBlank @Email @Size(max = 254)
        String newEmail,

        @NotBlank
        String currentPassword) {
}
