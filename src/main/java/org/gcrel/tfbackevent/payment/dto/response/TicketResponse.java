package org.gcrel.tfbackevent.payment.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Ticket;

public record TicketResponse(
        UUID id,
        UUID eventId,
        String eventTitle,
        String status,
        LocalDateTime boughtAt,
        String qrCodeBase64) {

    public TicketResponse(Ticket ticket, String qr) {
        this(
                ticket.getId(),
                ticket.getEvent().getId(),
                ticket.getEvent().getTitle(),
                ticket.getStatus().toString(),
                ticket.getBoughtAt(),
                qr);
    }
}
