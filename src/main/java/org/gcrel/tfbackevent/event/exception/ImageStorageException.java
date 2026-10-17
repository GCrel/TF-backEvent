package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.InternalServerException;

public class ImageStorageException extends InternalServerException {
    public ImageStorageException() {
        super("No se pudo guardar la imagen.");
    }
}
