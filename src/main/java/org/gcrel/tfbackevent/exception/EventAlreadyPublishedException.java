package org.gcrel.tfbackevent.exception;

public class EventAlreadyPublishedException extends RuntimeException {
    public EventAlreadyPublishedException() {
        super("El evento ya está publicado.");
    }
}
