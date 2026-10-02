package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.InternalServerException;

public class EventOrganizerMissingException extends InternalServerException {
    public EventOrganizerMissingException() {
        super("El evento no tiene un organizador válido.");
    }
}
