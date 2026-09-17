package org.gcrel.tfbackevent.exception;

public class MpOAuthException extends RuntimeException {
    public MpOAuthException(String reason) {
        super("Error al vincular cuenta de MercadoPago: " + reason);
    }
}
