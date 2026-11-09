package org.gcrel.tfbackevent.auth.exception;

import org.gcrel.tfbackevent.common.exception.ForbiddenException;

// 403 y no 401: el usuario sigue autenticado, y el front trata un 401 como sesión vencida.
public class IncorrectPasswordException extends ForbiddenException {
    public IncorrectPasswordException() {
        super("La contraseña es incorrecta.");
    }
}
