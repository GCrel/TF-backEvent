package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.BadGatewayException;

public class MpOAuthException extends BadGatewayException {
    public MpOAuthException(String reason) {
        super("Error al vincular cuenta de MercadoPago: " + reason);
    }
}
