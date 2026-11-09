package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

// Un cambio de sectores que afectaría entradas ya vendidas o reservadas.
public class SectorHasTicketsException extends ConflictException {
    public SectorHasTicketsException(String message) {
        super(message);
    }
}
