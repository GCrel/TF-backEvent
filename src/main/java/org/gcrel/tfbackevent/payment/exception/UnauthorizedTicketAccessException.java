package org.gcrel.tfbackevent.payment.exception;

public class UnauthorizedTicketAccessException extends RuntimeException {
    public UnauthorizedTicketAccessException() {
        super("No tenés permisos para acceder a esta entrada.");
    }
}
