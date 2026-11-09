package org.gcrel.tfbackevent.access.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignControllerRequest(@NotBlank @Email @Size(max = 254) String email) {
}
