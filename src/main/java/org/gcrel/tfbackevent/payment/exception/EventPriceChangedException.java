package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class EventPriceChangedException extends ConflictException {
    public EventPriceChangedException() {
        super("El precio del evento cambió mientras se procesaba la compra. Intentá nuevamente.");
    }
}
