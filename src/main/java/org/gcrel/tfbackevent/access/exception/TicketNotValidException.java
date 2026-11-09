package org.gcrel.tfbackevent.access.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class TicketNotValidException extends ConflictException {
    public TicketNotValidException() {
        super("El ticket no es válido: está cancelado o su pago aún no fue confirmado.");
    }
}
