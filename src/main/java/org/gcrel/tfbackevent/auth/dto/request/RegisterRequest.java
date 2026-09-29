package org.gcrel.tfbackevent.auth.dto.request;

import java.time.LocalDate;

public record RegisterRequest(
        String name,
        String email,
        String password,
        LocalDate dateBirth) {
}
