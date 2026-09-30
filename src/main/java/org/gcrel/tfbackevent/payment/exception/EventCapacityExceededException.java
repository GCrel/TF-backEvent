package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class EventCapacityExceededException extends ConflictException {
    public EventCapacityExceededException() {
        super("No hay cupos disponibles para este evento.");
    }
}
