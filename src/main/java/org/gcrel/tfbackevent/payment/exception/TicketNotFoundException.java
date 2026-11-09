package org.gcrel.tfbackevent.payment.exception;

import java.util.UUID;

import org.gcrel.tfbackevent.common.exception.NotFoundException;

public class TicketNotFoundException extends NotFoundException {
    public TicketNotFoundException(UUID id) {
        super("Entrada no encontrada con id: " + id);
    }
}
