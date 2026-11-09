package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.PaymentRequiredException;

public class PaymentRejectedException extends PaymentRequiredException {
    public PaymentRejectedException(String reason) {
        super("El pago fue rechazado: " + reason);
    }
}
