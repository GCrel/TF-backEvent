package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.ForbiddenException;

public class UnauthorizedEventAccessException extends ForbiddenException {
    public UnauthorizedEventAccessException() {
        super("No tenés permisos para modificar este evento.");
    }
}
