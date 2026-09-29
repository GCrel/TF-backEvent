package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class EventNotAvailableException extends ConflictException {
    public EventNotAvailableException() {
        super("El evento no está disponible para la compra de entradas.");
    }
}
