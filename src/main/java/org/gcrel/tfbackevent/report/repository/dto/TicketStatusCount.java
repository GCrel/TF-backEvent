package org.gcrel.tfbackevent.report.repository.dto;

import org.gcrel.tfbackevent.model.enums.TicketStatus;

public record TicketStatusCount(TicketStatus status, long count) {
}
