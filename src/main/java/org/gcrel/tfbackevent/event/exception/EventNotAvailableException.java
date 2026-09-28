package org.gcrel.tfbackevent.event.exception;

public class EventNotAvailableException extends RuntimeException {
    public EventNotAvailableException() {
        super("El evento no está disponible para la compra de entradas.");
    }
}
