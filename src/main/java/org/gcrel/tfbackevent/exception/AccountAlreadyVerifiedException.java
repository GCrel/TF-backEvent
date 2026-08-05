package org.gcrel.tfbackevent.exception;

public class AccountAlreadyVerifiedException extends RuntimeException {
    public AccountAlreadyVerifiedException() {
        super("El usuario ya está verificado");
    }
}
