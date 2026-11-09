package org.gcrel.tfbackevent.common.exception;

import org.springframework.http.HttpStatus;

public class PaymentRequiredException extends AppException {
    public PaymentRequiredException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED);
    }
}
