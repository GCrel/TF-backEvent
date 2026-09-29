package org.gcrel.tfbackevent.common.exception;

import org.springframework.http.HttpStatus;

public abstract class BadGatewayException extends AppException {
    public BadGatewayException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public BadGatewayException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY);
    }
}
