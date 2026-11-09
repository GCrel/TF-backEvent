package org.gcrel.tfbackevent.event.exception;

import org.gcrel.tfbackevent.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class UnsupportedImageTypeException extends AppException {
    public UnsupportedImageTypeException() {
        super("La imagen debe ser JPEG, PNG o WebP.", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
}
