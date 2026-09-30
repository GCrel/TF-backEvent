package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.BadRequestException;

public class InvalidTicketPurchaseException extends BadRequestException {
    public InvalidTicketPurchaseException(String message) {
        super(message);
    }
}
