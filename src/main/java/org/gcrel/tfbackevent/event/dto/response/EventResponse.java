package org.gcrel.tfbackevent.event.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isOrganizer,

        String organizerName,
        // Ruta relativa que sirve Nginx; null si el evento no tiene imagen.
        String imageUrl,

        // Plano y sectores: solo en el detalle de eventos presenciales; se omiten en listados.
        @JsonInclude(JsonInclude.Include.NON_NULL) String layoutImageUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<SectorResponse> sectors) {

    private static final String IMAGE_PATH = "/api/v1/images/";

    private static String imageUrl(String key) {
        return key == null ? null : IMAGE_PATH + key;
    }

    // Physical Event Constructor; `sectors` null indica un listado (sin plano ni sectores).
    public EventResponse(InPersonEvent event, long reservedTickets, Boolean isOrganizer,
            String organizerName, List<SectorResponse> sectors) {
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
                isOrganizer,
                organizerName,
                imageUrl(event.getImageKey()),
                sectors == null ? null : imageUrl(event.getLayoutImageKey()),
                sectors);
    }

    // Virtual Event Constructor
    public EventResponse(VirtualEvent event, Boolean isOrganizer, String organizerName) {
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
                isOrganizer,
                organizerName,
                imageUrl(event.getImageKey()),
                null,
                null);
    }
}
