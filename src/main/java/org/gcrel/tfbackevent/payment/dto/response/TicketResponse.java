package org.gcrel.tfbackevent.payment.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Ticket;

public record TicketResponse(
        UUID id,
        UUID eventId,
        String eventTitle,
        // Nombre del sector, o null si el evento no tiene sectores.
        String sectorName,
        String status,
        LocalDateTime boughtAt,
        String qrCodeBase64) {

    public TicketResponse(Ticket ticket, String qr) {
        this(
                ticket.getId(),
                ticket.getEvent().getId(),
                ticket.getEvent().getTitle(),
                ticket.getOrder().getSector() == null ? null : ticket.getOrder().getSector().getName(),
                ticket.getStatus().toString(),
                ticket.getBoughtAt(),
                qr);
    }
}
