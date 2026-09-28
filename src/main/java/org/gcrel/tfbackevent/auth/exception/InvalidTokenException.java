package org.gcrel.tfbackevent.auth.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("El token es inválido o ha expirado.");
    }
}
