package org.gcrel.tfbackevent.auth.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("El correo electrónico no ha sido verificado.");
    }
}
