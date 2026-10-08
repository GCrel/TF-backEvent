package org.gcrel.tfbackevent.access.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class TicketNotValidException extends ConflictException {
    public TicketNotValidException() {
        super("Ticket no es valido: (Caselado o, no comprado, o ya usado)");
    }
}
