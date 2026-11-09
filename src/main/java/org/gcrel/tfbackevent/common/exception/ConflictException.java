package org.gcrel.tfbackevent.common.exception;

import org.springframework.http.HttpStatus;

public abstract class ConflictException extends AppException {
    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
