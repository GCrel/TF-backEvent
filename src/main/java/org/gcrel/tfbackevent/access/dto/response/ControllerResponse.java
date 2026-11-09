package org.gcrel.tfbackevent.access.dto.response;

import java.util.UUID;

import org.gcrel.tfbackevent.model.User;

public record ControllerResponse(
        UUID userId,
        String name,
        String email) {

    public ControllerResponse(User user) {
        this(user.getId(), user.getName(), user.getEmail());
    }
}
