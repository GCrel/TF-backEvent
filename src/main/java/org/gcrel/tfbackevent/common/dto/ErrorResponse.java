package org.gcrel.tfbackevent.common.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
        String error,
        int status,
        LocalDateTime timestamp) {
}
