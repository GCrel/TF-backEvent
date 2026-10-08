package org.gcrel.tfbackevent.access.exception;

import org.gcrel.tfbackevent.common.exception.NotFoundException;

public class ControllerNotAssignedException extends NotFoundException {
    public ControllerNotAssignedException() {
        super("Controlador no asignado");
    }
}
