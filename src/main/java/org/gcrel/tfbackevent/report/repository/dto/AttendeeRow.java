package org.gcrel.tfbackevent.report.repository.dto;

import java.time.LocalDateTime;

import org.gcrel.tfbackevent.model.enums.TicketStatus;

public record AttendeeRow(String name, TicketStatus status, LocalDateTime boughtAt, LocalDateTime accessedAt) {
}
