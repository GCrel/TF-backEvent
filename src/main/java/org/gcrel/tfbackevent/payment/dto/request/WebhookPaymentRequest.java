package org.gcrel.tfbackevent.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class WebhookPaymentRequest {
    private String action;
    private String type;
    private Data data;

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private String id;
    }
}
