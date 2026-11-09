package org.gcrel.tfbackevent.report.dto.response;

import java.time.LocalDateTime;

import org.gcrel.tfbackevent.model.enums.TicketStatus;

// Sin email ni otros datos de contacto.
public record AttendeeResponse(String name, TicketStatus status, LocalDateTime boughtAt, LocalDateTime accessedAt) {
}
