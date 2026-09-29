package org.gcrel.tfbackevent.common.exception;

import org.springframework.http.HttpStatus;

public abstract class PreconditionRequiredException extends AppException {
    public PreconditionRequiredException(String message) {
        super(message, HttpStatus.PRECONDITION_REQUIRED);
    }
}
