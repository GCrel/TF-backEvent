package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.UnauthorizedException;

public class InvalidTokenException extends UnauthorizedException {
    public InvalidTokenException() {
        super("El token es inválido o ha expirado.");
    }
}
