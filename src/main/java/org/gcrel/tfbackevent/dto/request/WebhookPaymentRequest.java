package org.gcrel.tfbackevent.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class WebhookPaymentRequest {
    private String action;
    private Data data;

    public String getAction() { return action; }
    public Data getData() { return data; }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private String id;

    }
}