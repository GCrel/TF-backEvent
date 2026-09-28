package org.gcrel.tfbackevent.payment.exception;

public class WebhookValidationException extends RuntimeException {
    public WebhookValidationException() {
        super("Firma del webhook inválida.");
    }
}
