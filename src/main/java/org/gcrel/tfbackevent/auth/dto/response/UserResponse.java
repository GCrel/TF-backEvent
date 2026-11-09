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
        LocalDate dateBirth,
        // Ruta relativa que sirve Nginx; null si el usuario no tiene avatar.
        String avatarUrl) {
    private static final String IMAGE_PATH = "/api/v1/images/";

    public UserResponse(User user) {
        this(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getSystemRole(),
                user.getDateBirth(),
                user.getAvatarKey() == null ? null : IMAGE_PATH + user.getAvatarKey());
    }
}
