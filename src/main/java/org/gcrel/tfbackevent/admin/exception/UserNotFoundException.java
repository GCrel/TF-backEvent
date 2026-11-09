package org.gcrel.tfbackevent.admin.exception;

import java.util.UUID;

import org.gcrel.tfbackevent.common.exception.NotFoundException;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(UUID id) {
        super("Usuario no encontrado con id: " + id);
    }
}
