package org.gcrel.tfbackevent.auth.exception;

public class InvalidVerificationCodeException extends RuntimeException {
    public InvalidVerificationCodeException() {
        super("Código incorrecto");
    }
}
