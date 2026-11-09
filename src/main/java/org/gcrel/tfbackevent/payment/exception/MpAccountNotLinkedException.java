package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.PreconditionRequiredException;

public class MpAccountNotLinkedException extends PreconditionRequiredException {
    public MpAccountNotLinkedException() {
        super("El organizador no tiene una cuenta de MercadoPago vinculada.");
    }
}
