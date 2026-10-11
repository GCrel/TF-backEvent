package org.gcrel.tfbackevent.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ticket.purchase")
public record TicketPurchaseProperties(
        @DefaultValue("10") int maxQuantity) {
}
