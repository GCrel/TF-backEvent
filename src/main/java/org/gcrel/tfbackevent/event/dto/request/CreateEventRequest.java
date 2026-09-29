package org.gcrel.tfbackevent.event.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateEventRequest(
        String title,
        String description,
        LocalDateTime dateTime,
        BigDecimal basePrice,
        String eventType, // "IN_PERSON" | "VIRTUAL"

        // Physical Event Fields
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer capacity,

        // Virtual Event Fields
        String accessLink,
        String platform,
        String accessInstructions) {
}
