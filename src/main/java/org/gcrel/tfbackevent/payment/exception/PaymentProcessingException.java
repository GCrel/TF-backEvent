package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.BadGatewayException;

public class PaymentProcessingException extends BadGatewayException {
    private final boolean outcomeUnknown;

    public PaymentProcessingException(String reason, Throwable cause) {
        this(reason, cause, true);
    }

    public PaymentProcessingException(String reason, Throwable cause, boolean outcomeUnknown) {
        super("No se pudo confirmar el estado del pago con MercadoPago: " + reason, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
