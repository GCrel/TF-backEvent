package org.gcrel.tfbackevent.report.repository.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EventRevenue(UUID eventId, String title, BigDecimal revenue, long tickets) {
}
