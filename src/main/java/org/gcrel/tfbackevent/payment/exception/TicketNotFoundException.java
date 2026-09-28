package org.gcrel.tfbackevent.payment.exception;

import java.util.UUID;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(UUID id) {
        super("Entrada no encontrada con id: " + id);
    }
}
