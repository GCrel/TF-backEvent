package org.gcrel.tfbackevent.exception;

public class InvalidEventTypeException extends RuntimeException {
    public InvalidEventTypeException() {
        super("Tipo de evento inválido. Los valores permitidos son: IN_PERSON, VIRTUAL.");
    }
}