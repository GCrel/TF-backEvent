package org.gcrel.tfbackevent.admin.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;

public record TicketAdminResponse(
        UUID id,
        UUID eventId,
        String eventTitle,
        UUID attendeeId,
        String attendeeName,
        // Null si el evento no tiene sectores.
        String sectorName,
        TicketStatus status,
        LocalDateTime boughtAt,
        LocalDateTime accessedAt,
        UUID orderId,
        DeliveryStatus deliveryStatus) {
    public TicketAdminResponse(Ticket ticket) {
        this(ticket.getId(), ticket.getEvent().getId(), ticket.getEvent().getTitle(),
                ticket.getAttendee().getId(), ticket.getAttendee().getName(),
                ticket.getOrder().getSector() == null ? null : ticket.getOrder().getSector().getName(),
                ticket.getStatus(), ticket.getBoughtAt(), ticket.getAccessedAt(),
                ticket.getOrder().getId(), ticket.getOrder().getDeliveryStatus());
    }
}
