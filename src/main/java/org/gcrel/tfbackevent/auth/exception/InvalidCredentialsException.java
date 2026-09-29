package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {
    public InvalidCredentialsException() {
        super("Credenciales inválidas.");
    }
}
