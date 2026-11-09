package org.gcrel.tfbackevent.report.repository.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleRow(LocalDateTime date, int tickets, BigDecimal amount) {
}
