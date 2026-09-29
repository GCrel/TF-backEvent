package org.gcrel.tfbackevent.common.exception;

import org.springframework.http.HttpStatus;

public abstract class UnauthorizedException extends AppException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
