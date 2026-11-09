package org.gcrel.tfbackevent.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.gcrel.tfbackevent.access.service.EventAuthorizationService;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.report.dto.response.AttendeeResponse;
import org.gcrel.tfbackevent.report.dto.response.EventAttendanceResponse;
import org.gcrel.tfbackevent.report.dto.response.EventAttendanceResponse.HourlyAccess;
import org.gcrel.tfbackevent.report.dto.response.EventSalesResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSalesResponse.DailySales;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse.PaymentCounts;
import org.gcrel.tfbackevent.report.dto.response.OrganizerSummaryResponse;
import org.gcrel.tfbackevent.report.dto.response.OrganizerSummaryResponse.TopEvent;
import org.gcrel.tfbackevent.report.exception.ReportAccessDeniedException;
import org.gcrel.tfbackevent.report.repository.ReportRepository;
import org.gcrel.tfbackevent.report.repository.dto.AttendeeRow;
import org.gcrel.tfbackevent.report.repository.dto.ControllerValidations;
import org.gcrel.tfbackevent.report.repository.dto.EventStatusCount;
import org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals;
import org.gcrel.tfbackevent.report.repository.dto.SaleRow;
import org.gcrel.tfbackevent.report.repository.dto.TicketStatusCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;
    private static final int HOURS_PER_DAY = 24;
    private static final int TOP_ORGANIZER_EVENTS = 5;
    private static final int CSV_PAGE_SIZE = 500;
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Más validaciones primero; el desempate es fijo para que el orden no dependa
    // de la base.
    private static final Comparator<ControllerValidations> CONTROLLER_ORDER = Comparator
            .comparingLong(ControllerValidations::count).reversed()
            .thenComparing(ControllerValidations::name)
            .thenComparing(ControllerValidations::controllerId);

    private final EventRepository eventRepository;
    private final EventAuthorizationService authorization;
    private final ReportRepository reports;
    private final Clock clock;

    public ReportService(
            EventRepository eventRepository,
            EventAuthorizationService authorization,
            ReportRepository reports,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.authorization = authorization;
        this.reports = reports;
        this.clock = clock;
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

    // Ventas por día
    @Transactional(readOnly = true)
    public EventSalesResponse eventSales(UUID eventId, User viewer) {
        Event event = authorizedEvent(eventId, viewer);

        List<SaleRow> sales = new ArrayList<>(reports.approvedSales(eventId));
        sales.addAll(reports.freeSales(eventId));
        if (sales.isEmpty()) {
            return new EventSalesResponse(eventId, List.of());
        }

        Map<LocalDate, Long> ticketsByDay = new HashMap<>();
        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        for (SaleRow sale : sales) {
            LocalDate day = sale.date().toLocalDate();
            ticketsByDay.merge(day, (long) sale.tickets(), Long::sum);
            revenueByDay.merge(day, sale.amount(), BigDecimal::add);
        }
        LocalDate first = Collections.min(ticketsByDay.keySet());
        LocalDate lastSale = Collections.max(ticketsByDay.keySet());
        LocalDate end = max(lastSale, min(LocalDate.now(clock), event.getDateTime().toLocalDate()));

        List<DailySales> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(new DailySales(day, ticketsByDay.getOrDefault(day, 0L),
                    money(revenueByDay.getOrDefault(day, BigDecimal.ZERO))));
        }
        return new EventSalesResponse(eventId, days);
    }

    @Transactional(readOnly = true)
    public EventAttendanceResponse eventAttendance(UUID eventId, User viewer) {
        Event event = authorizedEvent(eventId, viewer);

        long[] accessesByHour = new long[HOURS_PER_DAY];
        reports.accessTimes(eventId).forEach(time -> accessesByHour[time.getHour()]++);
        List<HourlyAccess> byHour = IntStream.range(0, HOURS_PER_DAY)
                .mapToObj(hour -> new HourlyAccess(hour, accessesByHour[hour]))
                .toList();

        List<EventAttendanceResponse.ControllerValidations> byController = event instanceof InPersonEvent
                ? reports.validationsByController(eventId).stream()
                        .sorted(CONTROLLER_ORDER)
                        .map(row -> new EventAttendanceResponse.ControllerValidations(
                                row.controllerId(), row.name(), row.count()))
                        .toList()
                : null;
        return new EventAttendanceResponse(eventId, byHour, byController);
    }

    @Transactional(readOnly = true)
    public Page<AttendeeResponse> attendees(UUID eventId, User viewer, Pageable pageable) {
        authorizedEvent(eventId, viewer);
        return reports.attendees(eventId, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(row -> new AttendeeResponse(row.name(), row.status(), row.boughtAt(), row.accessedAt()));
    }

    // shortcut: arma el archivo en memoria; pasar a streaming si un evento supera
    // ~100.000 entradas.
    @Transactional(readOnly = true)
    public String attendeesCsv(UUID eventId, User viewer) {
        authorizedEvent(eventId, viewer);
        StringBuilder csv = new StringBuilder(Csv.BOM)
                .append(Csv.row("Nombre", "Estado", "Fecha de compra", "Fecha de acceso"));
        Page<AttendeeRow> page;
        int number = 0;
        do {
            page = reports.attendees(eventId, PageRequest.of(number++, CSV_PAGE_SIZE));
            page.forEach(row -> csv.append(Csv.row(row.name(), statusLabel(row.status()),
                    CSV_DATE.format(row.boughtAt()),
                    row.accessedAt() == null ? null : CSV_DATE.format(row.accessedAt()))));
        } while (page.hasNext());
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public OrganizerSummaryResponse organizerSummary(User organizer) {
        List<UUID> eventIds = reports.organizedEventIds(organizer.getId());
        if (eventIds.isEmpty()) {
            return new OrganizerSummaryResponse(0, 0, money(BigDecimal.ZERO), new PaymentCounts(0, 0, 0),
                    eventsByStatus(List.of()), List.of());
        }

        Map<TicketStatus, Long> tickets = ticketCounts(reports.ticketsByStatusIn(eventIds));
        Map<PaymentStatus, PaymentStatusTotals> payments = paymentTotals(reports.paymentsByStatusIn(eventIds));
        List<TopEvent> topEvents = reports.topEventsByRevenueIn(eventIds, PageRequest.of(0, TOP_ORGANIZER_EVENTS))
                .stream()
                .map(row -> new TopEvent(row.eventId(), row.title(), money(row.revenue()), row.tickets()))
                .toList();

        return new OrganizerSummaryResponse(
                tickets.get(TicketStatus.ACTIVE) + tickets.get(TicketStatus.USED),
                tickets.get(TicketStatus.PENDING),
                money(payments.get(PaymentStatus.APPROVED).total()),
                new PaymentCounts(payments.get(PaymentStatus.APPROVED).count(),
                        payments.get(PaymentStatus.REJECTED).count(), payments.get(PaymentStatus.PENDING).count()),
                eventsByStatus(reports.eventsByStatusIn(eventIds)),
                topEvents);
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

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String statusLabel(TicketStatus status) {
        return status == TicketStatus.USED ? "Usada" : "Activa";
    }

    private static Map<EventStatus, Long> eventsByStatus(List<EventStatusCount> rows) {
        Map<EventStatus, Long> counts = new EnumMap<>(EventStatus.class);
        for (EventStatus status : EventStatus.values()) {
            counts.put(status, 0L);
        }
        rows.forEach(row -> counts.put(row.status(), row.count()));
        return counts;
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
