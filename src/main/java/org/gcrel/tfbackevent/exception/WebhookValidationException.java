package org.gcrel.tfbackevent.exception;

public class WebhookValidationException extends RuntimeException {
    public WebhookValidationException() {
        super("Firma del webhook inválida.");
    }
}
