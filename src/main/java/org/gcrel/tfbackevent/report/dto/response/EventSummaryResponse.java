package org.gcrel.tfbackevent.report.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;

// Las tasas son proporciones de 0 a 1 (4 decimales) y valen null si el denominador es 0.
public record EventSummaryResponse(
        UUID eventId,
        String title,
        EventType eventType,
        EventStatus status,
        // Tickets ACTIVE o USED.
        long ticketsSold,
        // Tickets PENDING (reservados, a la espera del pago).
        long ticketsPending,
        // Suma de los pagos APPROVED, bruta (sin comisiones ni reembolsos), 2 decimales.
        BigDecimal revenue,
        // revenue / ticketsSold, 2 decimales; null si no hay entradas vendidas.
        BigDecimal averageTicketPrice,
        PaymentCounts payments,
        // aprobados / (aprobados + rechazados)
        BigDecimal approvalRate,
        // Solo presenciales; null en eventos virtuales.
        Integer capacity,
        // ticketsSold / capacity; null en virtuales o con capacidad 0.
        BigDecimal occupancyRate,
        // Tickets vendidos con accessedAt: se completa al escanear (presencial) o en la primera consulta
        // de credenciales (virtual).
        long attendance,
        // attendance / ticketsSold
        BigDecimal attendanceRate) {

    public record PaymentCounts(long approved, long rejected, long pending) {
    }
}
