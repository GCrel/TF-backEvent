package org.gcrel.tfbackevent.payment.dto.request;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class PurchaseTicketRequest {
    private UUID eventId;
    private String cardToken;
    private Integer installments;
    private String payerEmail;
    private String paymentMethodId;
}
