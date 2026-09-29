package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class EventNotModifiableException extends ConflictException {
    public EventNotModifiableException() {
        super("El evento no puede modificarse porque está cancelado o finalizado.");
    }
}
