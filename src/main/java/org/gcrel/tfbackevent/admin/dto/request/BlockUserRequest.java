package org.gcrel.tfbackevent.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockUserRequest(
        @NotBlank(message = "Reason is required") @Size(max = 500)
        String reason) {
}
