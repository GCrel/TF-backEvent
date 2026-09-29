package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.ForbiddenException;

public class EmailNotVerifiedException extends ForbiddenException {
    public EmailNotVerifiedException() {
        super("El correo electrónico no ha sido verificado.");
    }
}
