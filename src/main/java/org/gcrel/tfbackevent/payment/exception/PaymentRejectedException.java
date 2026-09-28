package org.gcrel.tfbackevent.payment.exception;

public class PaymentRejectedException extends RuntimeException {
    public PaymentRejectedException(String reason) {
        super("El pago fue rechazado: " + reason);
    }
}
