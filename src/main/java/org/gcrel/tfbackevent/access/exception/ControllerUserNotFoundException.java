package org.gcrel.tfbackevent.access.exception;

import org.gcrel.tfbackevent.common.exception.NotFoundException;

public class ControllerUserNotFoundException extends NotFoundException {
    public ControllerUserNotFoundException() {
        super("No se encontró un usuario habilitado para asignar como controlador.");
    }
}
