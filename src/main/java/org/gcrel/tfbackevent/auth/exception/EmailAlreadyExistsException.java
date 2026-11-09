package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class EmailAlreadyExistsException extends ConflictException {
    public EmailAlreadyExistsException(String email) {
        super("El email ya está registrado: " + email);
    }
}
