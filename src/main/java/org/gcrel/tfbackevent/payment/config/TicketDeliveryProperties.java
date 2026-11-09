package org.gcrel.tfbackevent.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Reintentos del envío de entradas por email: backoff exponencial
// (initial, 2x initial, 4x initial... hasta max) y un tope de intentos por ciclo.
@ConfigurationProperties(prefix = "ticket.delivery")
public record TicketDeliveryProperties(
        @DefaultValue("8") int maxAttempts,
        @DefaultValue("1") long initialBackoffMinutes,
        @DefaultValue("60") long maxBackoffMinutes) {
}
