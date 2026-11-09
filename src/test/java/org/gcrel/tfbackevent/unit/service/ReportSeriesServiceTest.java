package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.access.service.EventAuthorizationService;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.report.dto.response.EventAttendanceResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSalesResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSalesResponse.DailySales;
import org.gcrel.tfbackevent.report.exception.ReportAccessDeniedException;
import org.gcrel.tfbackevent.report.repository.ReportRepository;
import org.gcrel.tfbackevent.report.repository.dto.ControllerValidations;
import org.gcrel.tfbackevent.report.repository.dto.SaleRow;
import org.gcrel.tfbackevent.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Series por día y por hora: agrupado en Java, relleno de días vacíos y borde de medianoche. Las
// consultas que alimentan estas series las verifica ReportQueriesTest contra PostgreSQL.
@ExtendWith(MockitoExtension.class)
class ReportSeriesServiceTest {
    private static final LocalDate EVENT_DAY = LocalDate.of(2026, 3, 20);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventAuthorizationService authorization;
    @Mock
    private ReportRepository reports;

    private User organizer;
    private InPersonEvent event;

    @BeforeEach
    void setUp() {
        organizer = new User("Org", "org@example.com", "hash", LocalDate.of(1990, 1, 1));
        event = new InPersonEvent("Conf", "Desc", EVENT_DAY.atTime(18, 0), new BigDecimal("50.00"), "Venue",
                new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        event.setId(UUID.randomUUID());
        event.setStatus(EventStatus.PUBLISHED);
        lenient().when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        lenient().when(authorization.isOrganizer(event, organizer)).thenReturn(true);
        lenient().when(reports.approvedSales(event.getId())).thenReturn(List.of());
        lenient().when(reports.freeSales(event.getId())).thenReturn(List.of());
    }

    // "Hoy" fijo, para que la regla del fin de la serie no dependa del día en que
    // corre la prueba.
    private ReportService serviceOn(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new ReportService(eventRepository, authorization, reports, clock);
    }

    private static SaleRow sale(LocalDateTime date, int tickets, String amount) {
        return new SaleRow(date, tickets, new BigDecimal(amount));
    }

    private void paidSales(SaleRow... rows) {
        when(reports.approvedSales(event.getId())).thenReturn(List.of(rows));
    }

    private static void assertDay(DailySales day, LocalDate date, long tickets, String revenue) {
        assertEquals(date, day.date());
        assertEquals(tickets, day.tickets());
        assertEquals(new BigDecimal(revenue), day.revenue());
    }

    // ==========================================
    // Ventas por día
    // ==========================================

    @Test
    void eventSales_GroupsByDayFillsTheGapsAndEndsTodayWhileTheEventIsUpcoming() {
        paidSales(sale(LocalDateTime.of(2026, 3, 10, 23, 59, 30), 2, "100.00"),
                sale(LocalDateTime.of(2026, 3, 11, 0, 0), 1, "50.00"),
                sale(LocalDateTime.of(2026, 3, 12, 15, 0), 3, "150.00"));
        when(reports.freeSales(event.getId()))
                .thenReturn(List.of(sale(LocalDateTime.of(2026, 3, 14, 12, 0), 2, "0.00")));

        List<DailySales> days = serviceOn(LocalDate.of(2026, 3, 16)).eventSales(event.getId(), organizer).days();

        assertEquals(7, days.size());
        assertDay(days.get(0), LocalDate.of(2026, 3, 10), 2, "100.00");
        // 23:59:30 y 00:00 caen en días distintos.
        assertDay(days.get(1), LocalDate.of(2026, 3, 11), 1, "50.00");
        assertDay(days.get(2), LocalDate.of(2026, 3, 12), 3, "150.00");
        assertDay(days.get(3), LocalDate.of(2026, 3, 13), 0, "0.00");
        // La orden gratuita cuenta como venta con ingreso 0.
        assertDay(days.get(4), LocalDate.of(2026, 3, 14), 2, "0.00");
        assertDay(days.get(5), LocalDate.of(2026, 3, 15), 0, "0.00");
        assertDay(days.get(6), LocalDate.of(2026, 3, 16), 0, "0.00");
    }

    @Test
    void eventSales_OfAPastEvent_EndsOnTheEventDayInsteadOfDraggingZerosUpToToday() {
        paidSales(sale(LocalDateTime.of(2026, 3, 18, 10, 0), 1, "50.00"));

        List<DailySales> days = serviceOn(LocalDate.of(2026, 9, 1)).eventSales(event.getId(), organizer).days();

        assertEquals(3, days.size());
        assertEquals(LocalDate.of(2026, 3, 18), days.get(0).date());
        assertEquals(EVENT_DAY, days.get(2).date());
    }

    @Test
    void eventSales_NeverEndsBeforeTheLastSaleEvenIfItIsAfterTheEventDay() {
        // Las compras se aceptan hasta unas horas después de empezar el evento.
        paidSales(sale(LocalDateTime.of(2026, 3, 19, 10, 0), 1, "50.00"),
                sale(LocalDateTime.of(2026, 3, 21, 0, 30), 1, "50.00"));

        List<DailySales> days = serviceOn(LocalDate.of(2026, 9, 1)).eventSales(event.getId(), organizer).days();

        assertEquals(List.of(LocalDate.of(2026, 3, 19), EVENT_DAY, LocalDate.of(2026, 3, 21)),
                days.stream().map(DailySales::date).toList());
        assertEquals(1, days.get(2).tickets());
    }

    @Test
    void eventSales_OfAnUpcomingEvent_EndsTodayAndNotOnTheEventDay() {
        paidSales(sale(LocalDateTime.of(2026, 3, 10, 9, 0), 1, "50.00"));

        List<DailySales> days = serviceOn(LocalDate.of(2026, 3, 12)).eventSales(event.getId(), organizer).days();

        assertEquals(3, days.size());
        assertEquals(LocalDate.of(2026, 3, 12), days.get(2).date());
    }

    @Test
    void eventSales_SumsSeveralSalesOfTheSameDay() {
        paidSales(sale(LocalDateTime.of(2026, 3, 10, 9, 0), 1, "50.00"),
                sale(LocalDateTime.of(2026, 3, 10, 22, 0), 2, "99.99"));

        List<DailySales> days = serviceOn(LocalDate.of(2026, 3, 10)).eventSales(event.getId(), organizer).days();

        assertEquals(1, days.size());
        assertDay(days.get(0), LocalDate.of(2026, 3, 10), 3, "149.99");
    }

    @Test
    void eventSales_OfAFreeEventShowsItsTicketsWithZeroRevenue() {
        when(reports.freeSales(event.getId()))
                .thenReturn(List.of(sale(LocalDateTime.of(2026, 3, 10, 9, 0), 4, "0.00")));

        List<DailySales> days = serviceOn(LocalDate.of(2026, 3, 10)).eventSales(event.getId(), organizer).days();

        assertEquals(1, days.size());
        assertDay(days.get(0), LocalDate.of(2026, 3, 10), 4, "0.00");
    }

    @Test
    void eventSales_WithoutSales_ReturnsAnEmptySeries() {
        EventSalesResponse response = serviceOn(LocalDate.of(2026, 3, 10)).eventSales(event.getId(), organizer);

        assertEquals(event.getId(), response.eventId());
        assertEquals(List.of(), response.days());
    }

    // ==========================================
    // Accesos
    // ==========================================

    @Test
    void eventAttendance_AlwaysReturns24HoursAndSumsAccessesAcrossDays() {
        when(reports.accessTimes(event.getId())).thenReturn(List.of(
                LocalDateTime.of(2026, 3, 20, 9, 15), LocalDateTime.of(2026, 3, 20, 9, 45),
                LocalDateTime.of(2026, 3, 21, 9, 50), LocalDateTime.of(2026, 3, 20, 21, 5),
                LocalDateTime.of(2026, 3, 20, 0, 0), LocalDateTime.of(2026, 3, 20, 23, 59, 59)));
        when(reports.validationsByController(event.getId())).thenReturn(List.of());

        EventAttendanceResponse response = serviceOn(LocalDate.of(2026, 3, 20)).eventAttendance(event.getId(),
                organizer);

        assertEquals(24, response.byHour().size());
        for (int hour = 0; hour < 24; hour++) {
            assertEquals(hour, response.byHour().get(hour).hour());
        }
        assertEquals(1, response.byHour().get(0).accesses());
        assertEquals(3, response.byHour().get(9).accesses());
        assertEquals(1, response.byHour().get(21).accesses());
        assertEquals(1, response.byHour().get(23).accesses());
        assertEquals(0, response.byHour().get(10).accesses());
        assertEquals(6, response.byHour().stream().mapToLong(EventAttendanceResponse.HourlyAccess::accesses).sum());
    }

    @Test
    void eventAttendance_WithoutAccesses_StillReturns24ZeroRows() {
        when(reports.accessTimes(event.getId())).thenReturn(List.of());
        when(reports.validationsByController(event.getId())).thenReturn(List.of());

        EventAttendanceResponse response = serviceOn(LocalDate.of(2026, 3, 20)).eventAttendance(event.getId(),
                organizer);

        assertEquals(24, response.byHour().size());
        assertEquals(0, response.byHour().stream().mapToLong(EventAttendanceResponse.HourlyAccess::accesses).sum());
        assertEquals(List.of(), response.byController());
    }

    @Test
    void eventAttendance_ListsValidationsByControllerMostFirstWithAFixedTieBreak() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        when(reports.accessTimes(event.getId())).thenReturn(List.of());
        when(reports.validationsByController(event.getId())).thenReturn(List.of(
                new ControllerValidations(second, "Beto", 2), new ControllerValidations(third, "Ana", 2),
                new ControllerValidations(first, "Carlos", 5)));

        List<EventAttendanceResponse.ControllerValidations> byController = serviceOn(LocalDate.of(2026, 3, 20))
                .eventAttendance(event.getId(), organizer).byController();

        assertEquals(List.of("Carlos", "Ana", "Beto"),
                byController.stream().map(EventAttendanceResponse.ControllerValidations::name).toList());
        assertEquals(5, byController.get(0).validations());
        assertEquals(first, byController.get(0).controllerId());
    }

    @Test
    void eventAttendance_ForAVirtualEvent_OmitsTheControllers() {
        VirtualEvent virtual = new VirtualEvent("Webinar", "Desc", EVENT_DAY.atTime(18, 0), new BigDecimal("40.00"),
                "https://meet.example.com", "Meet", null);
        virtual.setId(UUID.randomUUID());
        virtual.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(virtual.getId())).thenReturn(Optional.of(virtual));
        when(authorization.isOrganizer(virtual, organizer)).thenReturn(true);
        when(reports.accessTimes(virtual.getId())).thenReturn(List.of(LocalDateTime.of(2026, 3, 12, 11, 0)));

        EventAttendanceResponse response = serviceOn(LocalDate.of(2026, 3, 20)).eventAttendance(virtual.getId(),
                organizer);

        assertNull(response.byController());
        assertEquals(1, response.byHour().get(11).accesses());
    }

    // ==========================================
    // Permisos
    // ==========================================

    @Test
    void series_ByANonOrganizer_ThrowForbiddenWithoutQueryingTheAggregates() {
        User stranger = new User("Otro", "otro@example.com", "hash", LocalDate.of(1990, 1, 1));
        when(authorization.isOrganizer(event, stranger)).thenReturn(false);
        ReportService service = serviceOn(LocalDate.of(2026, 3, 20));

        assertThrows(ReportAccessDeniedException.class, () -> service.eventSales(event.getId(), stranger));
        assertThrows(ReportAccessDeniedException.class, () -> service.eventAttendance(event.getId(), stranger));

        verifyNoInteractions(reports);
    }

    @Test
    void series_WhenTheEventDoesNotExist_ThrowNotFound() {
        UUID missing = UUID.randomUUID();
        when(eventRepository.findById(missing)).thenReturn(Optional.empty());
        ReportService service = serviceOn(LocalDate.of(2026, 3, 20));

        assertThrows(EventNotFoundException.class, () -> service.eventSales(missing, organizer));
        assertThrows(EventNotFoundException.class, () -> service.eventAttendance(missing, organizer));
    }
}
