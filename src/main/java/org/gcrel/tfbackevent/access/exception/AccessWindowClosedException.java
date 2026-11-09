package org.gcrel.tfbackevent.access.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class AccessWindowClosedException extends ConflictException {
    public AccessWindowClosedException() {
        super("La ventana de acceso para este evento está cerrada.");
    }
}
