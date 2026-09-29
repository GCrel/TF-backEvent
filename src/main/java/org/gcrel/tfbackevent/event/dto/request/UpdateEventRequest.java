package org.gcrel.tfbackevent.event.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateEventRequest(
        String title,
        String description,
        LocalDateTime dateTime,
        BigDecimal basePrice,

        // Physical Event
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer capacity,

        // Virtual Event
        String accessLink,
        String platform,
        String accessInstructions) {
}
