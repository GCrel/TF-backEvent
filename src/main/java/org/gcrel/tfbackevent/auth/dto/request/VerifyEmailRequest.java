package org.gcrel.tfbackevent.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank 
        @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits") 
        String code,
        
        @NotBlank @Email @Size(max = 254) 
        String email) {
}
