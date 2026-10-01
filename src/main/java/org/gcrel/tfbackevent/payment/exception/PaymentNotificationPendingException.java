package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class PaymentNotificationPendingException extends ConflictException {
    public PaymentNotificationPendingException() {
        super("El pago notificado todavía no está disponible localmente.");
    }
}
