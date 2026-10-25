package org.gcrel.tfbackevent.payment.repository.dto;

import java.time.LocalDateTime;

import org.gcrel.tfbackevent.model.enums.TicketStatus;

// validatedByName es null si el ticket no fue validado por un controlador.
public record TicketAccessState(TicketStatus status, LocalDateTime accessedAt, String validatedByName) {
}
