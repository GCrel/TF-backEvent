package org.gcrel.tfbackevent.exception;

public class UnauthorizedTicketAccessException extends RuntimeException {
    public UnauthorizedTicketAccessException() {
        super("No tenés permisos para acceder a esta entrada.");
    }
}
