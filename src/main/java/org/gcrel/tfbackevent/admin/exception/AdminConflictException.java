package org.gcrel.tfbackevent.admin.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

// La acción choca con el estado actual del recurso (ya bloqueado, ya es superusuario, etc.).
public class AdminConflictException extends ConflictException {
    public AdminConflictException(String message) {
        super(message);
    }
}
