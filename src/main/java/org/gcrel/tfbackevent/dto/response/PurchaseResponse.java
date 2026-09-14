package org.gcrel.tfbackevent.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@AllArgsConstructor
@Data
public class PurchaseResponse {
    private UUID ticketId;
    private String status;
    private String statusDetail;
    private String message;
    private String qrCodeBase64;
}
