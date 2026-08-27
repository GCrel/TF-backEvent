package org.gcrel.tfbackevent.exception;

import java.util.UUID;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(UUID id) {
        super("Entrada no encontrada con id: " + id);
    }
}
