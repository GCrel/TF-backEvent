package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class TicketAlreadyPurchasedException extends ConflictException {
    public TicketAlreadyPurchasedException() {
        super("El usuario ya tiene una compra pendiente o activa para este evento.");
    }
}
