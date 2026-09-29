package org.gcrel.tfbackevent.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WebhookPaymentRequest(
        String action,
        String type,
        @NotNull @Valid Data data) {

    public record Data(
            @NotBlank @Pattern (regexp = "^\\d{1,20}$") String id) {
    }
}
