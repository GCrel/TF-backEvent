package org.gcrel.tfbackevent.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private LocalDate dateBirth;
}
