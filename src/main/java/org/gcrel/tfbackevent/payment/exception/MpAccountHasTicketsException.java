package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class MpAccountHasTicketsException extends ConflictException {
    public MpAccountHasTicketsException() {
        super("No se puede desvincular Mercado Pago porque existen entradas vendidas.");
    }
}
