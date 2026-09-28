package org.gcrel.tfbackevent.payment.exception;

public class TicketNotActiveException extends RuntimeException {
    public TicketNotActiveException() {
        super("La entrada no está activa. Verificá el estado del pago.");
    }
}
