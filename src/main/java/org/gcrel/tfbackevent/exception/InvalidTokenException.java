package org.gcrel.tfbackevent.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("El token es inválido o ha expirado.");
    }
}