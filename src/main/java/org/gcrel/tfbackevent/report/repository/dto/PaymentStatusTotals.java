package org.gcrel.tfbackevent.report.repository.dto;

import java.math.BigDecimal;

import org.gcrel.tfbackevent.model.enums.PaymentStatus;

public record PaymentStatusTotals(PaymentStatus status, long count, BigDecimal total) {
}
