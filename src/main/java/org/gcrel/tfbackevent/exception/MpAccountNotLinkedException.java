package org.gcrel.tfbackevent.exception;

public class MpAccountNotLinkedException extends RuntimeException {
    public MpAccountNotLinkedException() {
        super("El organizador no tiene una cuenta de MercadoPago vinculada.");
    }
}
