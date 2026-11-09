package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.BadRequestException;

public class InvalidVerificationCodeException extends BadRequestException {
    public InvalidVerificationCodeException() {
        super("Código incorrecto");
    }
}
