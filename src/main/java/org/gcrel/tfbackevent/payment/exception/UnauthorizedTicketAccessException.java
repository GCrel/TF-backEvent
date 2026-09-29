package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.ForbiddenException;

public class UnauthorizedTicketAccessException extends ForbiddenException {
    public UnauthorizedTicketAccessException() {
        super("No tenés permisos para acceder a esta entrada.");
    }
}
