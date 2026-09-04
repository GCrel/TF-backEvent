package org.gcrel.tfbackevent.exception;

public class EventNotModifiableException extends RuntimeException {
    public EventNotModifiableException() {
        super("El evento no puede modificarse porque está cancelado o finalizado.");
    }
}
