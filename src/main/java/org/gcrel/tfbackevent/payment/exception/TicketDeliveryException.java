package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.BadGatewayException;

public class TicketDeliveryException extends BadGatewayException {
    public TicketDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
