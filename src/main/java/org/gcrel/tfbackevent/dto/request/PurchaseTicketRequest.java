package org.gcrel.tfbackevent.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@AllArgsConstructor
@Data
public class PurchaseTicketRequest {
    private UUID eventId;
    private String cardToken;
    private Integer installments;
    private String payerEmail;
}
