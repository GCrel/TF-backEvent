package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class TicketNotActiveException extends ConflictException {
    public TicketNotActiveException() {
        super("La entrada no está activa. Verificá el estado del pago.");
    }
}
