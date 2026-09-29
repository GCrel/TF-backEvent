package org.gcrel.tfbackevent.event.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventType;

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

        // Virtual Event
        String accessLink,
        String platform,
        String accessInstructions) {

    // Physical Event Constructor
    public EventResponse(InPersonEvent event) {
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
                null,
                null,
                null);
    }

    // Virtual Event Constructor
    public EventResponse(VirtualEvent event) {
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
                event.getAccessLink(),
                event.getPlatform(),
                event.getAccessInstructions());
    }
}
