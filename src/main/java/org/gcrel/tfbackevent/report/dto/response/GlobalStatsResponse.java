package org.gcrel.tfbackevent.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse.PaymentCounts;
import org.gcrel.tfbackevent.report.dto.response.OrganizerSummaryResponse.TopEvent;

public record GlobalStatsResponse(
        LocalDate from,
        LocalDate to,
        // Pagos aprobados del período más órdenes gratuitas emitidas en el período.
        long ticketsSold,
        BigDecimal revenue,
        // Pagos del período por estado, según la fecha del pago.
        PaymentCounts payments,
        Map<EventStatus, Long> eventsByStatus,
        // Hasta 10, de más a menos ingresos del período.
        List<TopEvent> topEvents,
        UserCounts users) {

    // newInPeriod: usuarios creados en el período; los anteriores a la columna created_at no cuentan.
    public record UserCounts(long total, long newInPeriod) {
    }
}
