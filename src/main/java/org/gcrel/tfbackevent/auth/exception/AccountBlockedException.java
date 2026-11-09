package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.ForbiddenException;

public class AccountBlockedException extends ForbiddenException {
    public AccountBlockedException() {
        super("Tu cuenta está bloqueada.");
    }
}
