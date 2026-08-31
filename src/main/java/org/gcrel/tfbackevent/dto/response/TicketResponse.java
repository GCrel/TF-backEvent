package org.gcrel.tfbackevent.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.gcrel.tfbackevent.model.Ticket;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@Data
public class TicketResponse {
    private UUID id;
    private UUID eventId;
    private String eventTitle;
    private String status;
    private LocalDateTime boughtAt;
    private String qrCodeBase64;

    public TicketResponse(Ticket ticket, String qr) {
        this.id = ticket.getId();
        this.eventId = ticket.getEvent().getId();
        this.eventTitle = ticket.getEvent().getTitle();
        this.status = ticket.getStatus().toString();
        this.boughtAt = ticket.getBoughtAt();
        this.qrCodeBase64 = qr;
    }
}
