package org.gcrel.tfbackevent.payment.dto.request;

public record WebhookPaymentRequest(
        String action,
        String type,
        Data data) {

    public record Data(
            String id) {
    }
}
