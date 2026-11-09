package org.gcrel.tfbackevent.auth.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 100)
        String name,
        
        @NotBlank(message = "Email is required") @Email @Size(max = 254) 
        String email,
        
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") 
        String password,
        
        @NotNull @Past(message = "Date of birth must be in the past") 
        LocalDate dateBirth) {
}
