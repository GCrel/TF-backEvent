package org.gcrel.tfbackevent.payment.dto.response;

import java.util.UUID;

public record PurchaseResponse(
        UUID ticketId,
        String status,
        String statusDetail,
        String message,
        String qrCodeBase64) {
}
