package org.gcrel.tfbackevent.access.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class ControllerAlreadyAssignedException extends ConflictException {
    public ControllerAlreadyAssignedException() {
        super("Controlador ya asignado");
    }
}
