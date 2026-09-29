package org.gcrel.tfbackevent.auth.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;

public record UserResponse(
        UUID id,
        String name,
        String email,
        SystemRole systemRole,
        LocalDate dateBirth) {
    public UserResponse(User user) {
        this(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getSystemRole(),
                user.getDateBirth());
    }
}
