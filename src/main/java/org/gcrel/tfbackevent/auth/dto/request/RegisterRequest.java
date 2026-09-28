package org.gcrel.tfbackevent.auth.dto.request;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private LocalDate dateBirth;
}
