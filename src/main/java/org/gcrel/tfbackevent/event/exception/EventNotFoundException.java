package org.gcrel.tfbackevent.event.exception;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(UUID id) {
        super("Evento no encontrado con id: " + id);
    }
}
