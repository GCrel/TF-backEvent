package org.gcrel.tfbackevent.payment.dto.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class PurchaseResponse {
    private UUID ticketId;
    private String status;
    private String statusDetail;
    private String message;
    private String qrCodeBase64;
}
