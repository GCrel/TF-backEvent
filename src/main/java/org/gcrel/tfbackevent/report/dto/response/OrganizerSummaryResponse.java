package org.gcrel.tfbackevent.report.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse.PaymentCounts;

public record OrganizerSummaryResponse(
        long ticketsSold,
        long ticketsPending,
        BigDecimal revenue,
        PaymentCounts payments,
        // Todos los estados, con 0 donde no hay eventos.
        Map<EventStatus, Long> eventsByStatus,
        // Hasta 5, de más a menos ingresos; solo eventos con pagos aprobados.
        List<TopEvent> topEvents) {

    // paidTickets: entradas de los pagos aprobados (no incluye las gratuitas).
    public record TopEvent(UUID eventId, String title, BigDecimal revenue, long paidTickets) {
    }
}
