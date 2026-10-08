package org.gcrel.tfbackevent.access.exception;

import java.time.LocalDateTime;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class TicketAlreadyUsedException extends ConflictException{
    public TicketAlreadyUsedException(LocalDateTime dateTime) {
        super("Ticket fue usado: " + dateTime.toString());
    }
}
