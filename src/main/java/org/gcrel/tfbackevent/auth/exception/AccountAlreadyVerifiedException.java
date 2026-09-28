package org.gcrel.tfbackevent.auth.exception;

public class AccountAlreadyVerifiedException extends RuntimeException {
    public AccountAlreadyVerifiedException() {
        super("El usuario ya está verificado");
    }
}
