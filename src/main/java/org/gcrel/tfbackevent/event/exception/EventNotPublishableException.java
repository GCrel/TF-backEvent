package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.BadRequestException;

public class EventNotPublishableException extends BadRequestException {
    public EventNotPublishableException() {
        super("No se puede publicar un evento cuya fecha ya pasó.");
    }
}
