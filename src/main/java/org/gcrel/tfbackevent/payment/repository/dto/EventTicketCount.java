package org.gcrel.tfbackevent.payment.repository.dto;

import java.util.UUID;

public record EventTicketCount(UUID eventId, long count) {
}
