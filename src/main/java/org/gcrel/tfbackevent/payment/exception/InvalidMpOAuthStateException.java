package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.BadRequestException;

public class InvalidMpOAuthStateException extends BadRequestException {
    public InvalidMpOAuthStateException() {
        super("El estado de autorización de MercadoPago es inválido o expiró.");
    }
}
