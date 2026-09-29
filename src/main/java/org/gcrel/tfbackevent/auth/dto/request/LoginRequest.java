package org.gcrel.tfbackevent.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email(message = "Invalid email format") 
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required") 
        @Size(min = 6, message = "Password must be at least 6 characters long") 
        String password) {
}
