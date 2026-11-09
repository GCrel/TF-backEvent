package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class EventHasTicketsException extends ConflictException {
    public EventHasTicketsException() {
        super("No se puede cancelar un evento que tiene entradas compradas.");
    }
}
