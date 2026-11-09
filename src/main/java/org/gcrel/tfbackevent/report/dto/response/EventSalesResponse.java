package org.gcrel.tfbackevent.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Una fila por día, desde la primera venta, con 0 en los días sin ventas. Vacía si no hubo ventas.
public record EventSalesResponse(UUID eventId, List<DailySales> days) {

    public record DailySales(
            LocalDate date,
            // Entradas vendidas ese día (pagos aprobados y órdenes gratuitas).
            long tickets,
            // Suma de los pagos aprobados ese día; 0.00 en eventos gratuitos.
            BigDecimal revenue) {
    }
}
