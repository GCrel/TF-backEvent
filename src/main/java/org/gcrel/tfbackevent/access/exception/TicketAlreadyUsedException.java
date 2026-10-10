package org.gcrel.tfbackevent.access.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class TicketAlreadyUsedException extends ConflictException {
    private static final DateTimeFormatter ACCESS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public TicketAlreadyUsedException(LocalDateTime dateTime) {
        super("Ticket fue usado: " + dateTime.format(ACCESS_DATE_FORMAT));
    }
}
