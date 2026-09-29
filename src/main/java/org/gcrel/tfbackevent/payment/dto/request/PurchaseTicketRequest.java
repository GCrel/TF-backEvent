package org.gcrel.tfbackevent.payment.dto.request;

import java.util.UUID;

public record PurchaseTicketRequest(
        UUID eventId,
        String cardToken,
        Integer installments,
        String payerEmail,
        String paymentMethodId) {
}
