package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class AccountAlreadyVerifiedException extends ConflictException {
    public AccountAlreadyVerifiedException() {
        super("El usuario ya está verificado");
    }
}
