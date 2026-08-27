package org.gcrel.tfbackevent.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

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
}
