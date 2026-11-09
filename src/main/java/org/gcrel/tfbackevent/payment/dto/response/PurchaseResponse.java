package org.gcrel.tfbackevent.payment.dto.response;

import java.util.List;
import java.util.UUID;

public record PurchaseResponse(
        UUID orderId,
        String status,
        String statusDetail,
        String message,
        List<PurchasedTicket> tickets) {

    // qrCodeBase64 solo se informa para entradas presenciales con pago aprobado.
    public record PurchasedTicket(UUID ticketId, String qrCodeBase64) {
    }
}
