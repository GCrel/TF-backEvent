package org.gcrel.tfbackevent.event.exception;

public class UnauthorizedEventAccessException extends RuntimeException {
    public UnauthorizedEventAccessException() {
        super("No tenés permisos para modificar este evento.");
    }
}
