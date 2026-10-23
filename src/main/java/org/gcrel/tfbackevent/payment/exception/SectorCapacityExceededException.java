package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class SectorCapacityExceededException extends ConflictException {
    public SectorCapacityExceededException() {
        super("No quedan suficientes entradas en el sector elegido.");
    }
}
