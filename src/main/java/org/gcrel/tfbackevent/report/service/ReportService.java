package org.gcrel.tfbackevent.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gcrel.tfbackevent.access.service.EventAuthorizationService;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse.PaymentCounts;
import org.gcrel.tfbackevent.report.exception.ReportAccessDeniedException;
import org.gcrel.tfbackevent.report.repository.ReportRepository;
import org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals;
import org.gcrel.tfbackevent.report.repository.dto.TicketStatusCount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;

    private final EventRepository eventRepository;
    private final EventAuthorizationService authorization;
    private final ReportRepository reports;

    public ReportService(
            EventRepository eventRepository,
            EventAuthorizationService authorization,
            ReportRepository reports) {
        this.eventRepository = eventRepository;
        this.authorization = authorization;
        this.reports = reports;
    }

    // Transaccional para que todas las consultas lean la misma conexión.
    @Transactional(readOnly = true)
    public EventSummaryResponse eventSummary(UUID eventId, User viewer) {
        Event event = authorizedEvent(eventId, viewer);

        Map<TicketStatus, Long> tickets = ticketCounts(reports.ticketsByStatus(eventId));
        long sold = tickets.get(TicketStatus.ACTIVE) + tickets.get(TicketStatus.USED);
        long pending = tickets.get(TicketStatus.PENDING);

        Map<PaymentStatus, PaymentStatusTotals> payments = paymentTotals(reports.paymentsByStatus(eventId));
        long approved = payments.get(PaymentStatus.APPROVED).count();
        long rejected = payments.get(PaymentStatus.REJECTED).count();
        BigDecimal revenue = money(payments.get(PaymentStatus.APPROVED).total());

        Integer capacity = event instanceof InPersonEvent inPerson ? inPerson.getCapacity() : null;
        long attendance = reports.attendance(eventId);

        return new EventSummaryResponse(
                eventId,
                event.getTitle(),
                event.getType(),
                event.getStatus(),
                sold,
                pending,
                revenue,
                sold == 0 ? null : revenue.divide(BigDecimal.valueOf(sold), MONEY_SCALE, RoundingMode.HALF_UP),
                new PaymentCounts(approved, rejected, payments.get(PaymentStatus.PENDING).count()),
                rate(approved, approved + rejected),
                capacity,
                capacity == null ? null : rate(sold, capacity),
                attendance,
                rate(attendance, sold));
    }

    private Event authorizedEvent(UUID eventId, User viewer) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (viewer.getSystemRole() != SystemRole.ADMIN && !authorization.isOrganizer(event, viewer)) {
            throw new ReportAccessDeniedException();
        }
        return event;
    }

    static BigDecimal rate(long numerator, long denominator) {
        return denominator <= 0
                ? null
                : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), RATE_SCALE,
                        RoundingMode.HALF_UP);
    }

    static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static Map<TicketStatus, Long> ticketCounts(List<TicketStatusCount> rows) {
        Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            counts.put(status, 0L);
        }
        rows.forEach(row -> counts.put(row.status(), row.count()));
        return counts;
    }

    private static Map<PaymentStatus, PaymentStatusTotals> paymentTotals(List<PaymentStatusTotals> rows) {
        Map<PaymentStatus, PaymentStatusTotals> totals = new EnumMap<>(PaymentStatus.class);
        for (PaymentStatus status : PaymentStatus.values()) {
            totals.put(status, new PaymentStatusTotals(status, 0, BigDecimal.ZERO));
        }
        rows.forEach(row -> totals.put(row.status(), row));
        return totals;
    }
}
