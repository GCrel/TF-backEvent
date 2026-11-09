package org.gcrel.tfbackevent.payment.exception;

import org.gcrel.tfbackevent.common.exception.UnauthorizedException;

public class WebhookValidationException extends UnauthorizedException {
    public WebhookValidationException() {
        super("Firma del webhook inválida.");
    }
}
