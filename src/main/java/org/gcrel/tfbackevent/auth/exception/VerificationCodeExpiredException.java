package org.gcrel.tfbackevent.auth.exception;

public class VerificationCodeExpiredException extends RuntimeException {
    public VerificationCodeExpiredException() {
        super("El código ha expirado");
    }
}
