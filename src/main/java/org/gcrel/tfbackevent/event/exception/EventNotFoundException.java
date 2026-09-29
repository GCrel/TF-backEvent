package org.gcrel.tfbackevent.event.exception;

import java.util.UUID;

import org.gcrel.tfbackevent.common.exception.NotFoundException;

public class EventNotFoundException extends NotFoundException {
    public EventNotFoundException(UUID id) {
        super("Evento no encontrado con id: " + id);
    }
}
