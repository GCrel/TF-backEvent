package org.gcrel.tfbackevent.payment.repository.dto;

import java.time.LocalDateTime;

import org.gcrel.tfbackevent.model.enums.TicketStatus;

public record TicketAccessState(TicketStatus status, LocalDateTime accessedAt) {
}
