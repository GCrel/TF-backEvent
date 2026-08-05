package org.gcrel.tfbackevent.exception;

public class InvalidVerificationCodeException extends RuntimeException {
    public InvalidVerificationCodeException() {
        super("Código incorrecto");
    }
}
