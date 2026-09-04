package org.gcrel.tfbackevent.exception;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(UUID id) {
        super("Evento no encontrado con id: " + id);
    }
}
