package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.InternalServerException;

public class QRGenerationException extends InternalServerException {
    public QRGenerationException() {
        super("Error al generar el código QR.");
    }
}
