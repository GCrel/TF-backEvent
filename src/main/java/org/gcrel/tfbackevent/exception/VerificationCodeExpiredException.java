package org.gcrel.tfbackevent.exception;

public class VerificationCodeExpiredException extends RuntimeException {
    public VerificationCodeExpiredException() {
        super("El código ha expirado");
    }
}
