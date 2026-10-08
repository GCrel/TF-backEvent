package org.gcrel.tfbackevent.access.exception;

import org.gcrel.tfbackevent.common.exception.BadRequestException;

public class TicketEventMismatchException extends BadRequestException {
    public TicketEventMismatchException() {
        super("Ticket no pertenece a este evento.");
    }
}
