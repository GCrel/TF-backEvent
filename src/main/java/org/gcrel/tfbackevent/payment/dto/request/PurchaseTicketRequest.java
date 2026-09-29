package org.gcrel.tfbackevent.payment.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PurchaseTicketRequest(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 255) String cardToken,
        @NotNull @Min(1) @Max(12) Integer installments,
        @NotBlank @Email @Size(max = 254) String payerEmail,
        @NotBlank @Size(max = 50) @Pattern (regexp = "^[a-z0-9_]+$") 
        String paymentMethodId) {
}
