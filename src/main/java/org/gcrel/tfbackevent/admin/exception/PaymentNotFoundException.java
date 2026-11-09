package org.gcrel.tfbackevent.admin.exception;

import java.util.UUID;

import org.gcrel.tfbackevent.common.exception.NotFoundException;

public class PaymentNotFoundException extends NotFoundException {
    public PaymentNotFoundException(UUID id) {
        super("Pago no encontrado con id: " + id);
    }
}
