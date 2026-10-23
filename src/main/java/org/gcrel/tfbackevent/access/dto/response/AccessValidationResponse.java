package org.gcrel.tfbackevent.access.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccessValidationResponse(
        UUID ticketId,
        String attendeeName,
        String eventTitle,
        String sectorName,
        LocalDateTime validatedAt) {
}
