package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.BadRequestException;

public class InvalidEventTypeException extends BadRequestException {
    public InvalidEventTypeException() {
        super("Tipo de evento inválido. Los valores permitidos son: IN_PERSON, VIRTUAL.");
    }
}
