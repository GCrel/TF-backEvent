package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class EventHasSalesException extends ConflictException {
    public EventHasSalesException() {
        super("No se puede cambiar el precio o la fecha de un evento con entradas vendidas.");
    }
}
