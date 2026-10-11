package org.gcrel.tfbackevent.auth.service.dto;

import java.util.UUID;

public record TicketQr(
        UUID ticketId,
        String qrBase64) {
}
