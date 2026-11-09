package org.gcrel.tfbackevent.admin.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;

public record UserAdminResponse(
        UUID id,
        String name,
        String email,
        SystemRole systemRole,
        boolean verified,
        boolean blocked,
        String blockedReason,
        LocalDateTime blockedAt,
        LocalDate dateBirth,
        String avatarUrl) {
    private static final String IMAGE_PATH = "/api/v1/images/";

    public UserAdminResponse(User user) {
        this(user.getId(), user.getName(), user.getEmail(), user.getSystemRole(), user.isEnabled(),
                user.isBlocked(), user.getBlockedReason(), user.getBlockedAt(), user.getDateBirth(),
                user.getAvatarKey() == null ? null : IMAGE_PATH + user.getAvatarKey());
    }
}
