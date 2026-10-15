package org.gcrel.tfbackevent.event.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventType;

import com.fasterxml.jackson.annotation.JsonInclude;

public record EventResponse(
        UUID id,
        String title,
        String description,
        LocalDateTime dateTime,
        String status,
        BigDecimal basePrice,
        String eventType,

        // Physical Event
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer capacity,
        Integer availableTickets,

        // Virtual Event
        String platform,
        @JsonInclude(JsonInclude.Include.NON_NULL) String accessLink,
        @JsonInclude(JsonInclude.Include.NON_NULL) String accessInstructions,

        // Solo en el detalle y en las respuestas al organizador; null (omitido) en listados.
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isOrganizer) {

    // Physical Event Constructor
    public EventResponse(InPersonEvent event, long reservedTickets, Boolean isOrganizer) {
        this(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getDateTime(),
                event.getStatus().name(),
                event.getBasePrice(),
                EventType.IN_PERSON.name(),
                event.getAddress(),
                event.getLatitude(),
                event.getLongitude(),
                event.getCapacity(),
                (int) Math.max(0, event.getCapacity() - reservedTickets),
                null,
                null,
                null,
                isOrganizer);
    }

    // Virtual Event Constructor
    public EventResponse(VirtualEvent event, Boolean isOrganizer) {
        this(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getDateTime(),
                event.getStatus().name(),
                event.getBasePrice(),
                EventType.VIRTUAL.name(),
                null,
                null,
                null,
                null,
                null,
                event.getPlatform(),
                Boolean.TRUE.equals(isOrganizer) ? event.getAccessLink() : null,
                Boolean.TRUE.equals(isOrganizer) ? event.getAccessInstructions() : null,
                isOrganizer);
    }
}
