package org.gcrel.tfbackevent.payment.dto.request;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PurchaseTicketRequest(
        @NotNull UUID eventId,
        // El máximo por orden se valida en el servicio (ticket.purchase.max-quantity).
        @NotNull @Min(1) Integer quantity,
        // Datos de pago: obligatorios solo si el evento tiene precio (se valida en el servicio).
        @Size(max = 255) String cardToken,
        @Min(1) @Max(12) Integer installments,
        @Email @Size(max = 254) String payerEmail,
        @Size(max = 50) @Pattern (regexp = "^[a-z0-9_]+$")
        String paymentMethodId) {

    @JsonIgnore
    public boolean hasPaymentData() {
        return cardToken != null && !cardToken.isBlank()
                && installments != null
                && payerEmail != null && !payerEmail.isBlank()
                && paymentMethodId != null && !paymentMethodId.isBlank();
    }
}
