package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class EventAlreadyPublishedException extends ConflictException {
    public EventAlreadyPublishedException() {
        super("El evento ya está publicado.");
    }
}
