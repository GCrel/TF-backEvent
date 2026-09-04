package org.gcrel.tfbackevent.exception;

public class UnauthorizedEventAccessException extends RuntimeException {
    public UnauthorizedEventAccessException() {
        super("No tenés permisos para modificar este evento.");
    }
}
