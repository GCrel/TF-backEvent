package org.gcrel.tfbackevent.access.exception;

import org.gcrel.tfbackevent.common.exception.NotFoundException;

public class ControllerUserNotFoundException extends NotFoundException {
    public ControllerUserNotFoundException(String message) {
        super(message);
    }
}
