package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.BadRequestException;

public class VerificationCodeExpiredException extends BadRequestException {
    public VerificationCodeExpiredException() {
        super("El código ha expirado");
    }
}
