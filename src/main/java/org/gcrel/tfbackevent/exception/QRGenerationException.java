package org.gcrel.tfbackevent.exception;

public class QRGenerationException extends RuntimeException {
    public QRGenerationException() {
        super("Error al generar el código QR.");
    }
}
