package org.gcrel.tfbackevent.common.exception;

import org.springframework.http.HttpStatus;

public abstract class InternalServerException extends AppException {
    public InternalServerException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
