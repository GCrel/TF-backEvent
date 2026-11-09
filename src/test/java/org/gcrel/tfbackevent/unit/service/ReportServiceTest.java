package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.access.service.EventAuthorizationService;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse;
import org.gcrel.tfbackevent.report.exception.ReportAccessDeniedException;
import org.gcrel.tfbackevent.report.repository.ReportRepository;
import org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals;
import org.gcrel.tfbackevent.report.repository.dto.TicketStatusCount;
import org.gcrel.tfbackevent.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventAuthorizationService authorization;
    @Mock
    private ReportRepository reports;

    @InjectMocks
    private ReportService service;

    private User organizer;
    private InPersonEvent event;

    @BeforeEach
    void setUp() {
        organizer = new User("Org", "org@example.com", "hash", LocalDate.of(1990, 1, 1));
        event = new InPersonEvent("Conf", "Desc", LocalDateTime.of(2026, 3, 20, 18, 0), new BigDecimal("50.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        event.setId(UUID.randomUUID());
        event.setStatus(EventStatus.PUBLISHED);
        lenient().when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
    }

    private void organizerAccess() {
        when(authorization.isOrganizer(event, organizer)).thenReturn(true);
    }

    private void stubTickets(long active, long used, long pending, long cancelled) {
        when(reports.ticketsByStatus(event.getId())).thenReturn(List.of(
                new TicketStatusCount(TicketStatus.ACTIVE, active), new TicketStatusCount(TicketStatus.USED, used),
                new TicketStatusCount(TicketStatus.PENDING, pending),
                new TicketStatusCount(TicketStatus.CANCELLED, cancelled)));
    }

    private void stubPayments(PaymentStatusTotals... totals) {
        when(reports.paymentsByStatus(event.getId())).thenReturn(List.of(totals));
    }

    private static PaymentStatusTotals payments(PaymentStatus status, long count, String total) {
        return new PaymentStatusTotals(status, count, new BigDecimal(total));
    }

    @Test
    void eventSummary_ComputesEveryFigureFromTheAggregates() {
        organizerAccess();
        stubTickets(5, 3, 2, 1);
        stubPayments(payments(PaymentStatus.APPROVED, 3, "300.00"), payments(PaymentStatus.REJECTED, 1, "50.00"),
                payments(PaymentStatus.PENDING, 1, "100.00"));
        when(reports.attendance(event.getId())).thenReturn(4L);

        EventSummaryResponse summary = service.eventSummary(event.getId(), organizer);

        assertEquals(EventType.IN_PERSON, summary.eventType());
        assertEquals(8, summary.ticketsSold());
        assertEquals(2, summary.ticketsPending());
        // Los pagos pendientes (100) y rechazados (50) no suman a los ingresos.
        assertEquals(new BigDecimal("300.00"), summary.revenue());
        assertEquals(new BigDecimal("37.50"), summary.averageTicketPrice());
        assertEquals(3, summary.payments().approved());
        assertEquals(1, summary.payments().rejected());
        assertEquals(1, summary.payments().pending());
        assertEquals(new BigDecimal("0.7500"), summary.approvalRate());
        assertEquals(100, summary.capacity());
        assertEquals(new BigDecimal("0.0800"), summary.occupancyRate());
        assertEquals(4, summary.attendance());
        assertEquals(new BigDecimal("0.5000"), summary.attendanceRate());
    }

    @Test
    void eventSummary_WithoutSales_ReturnsZerosAndNullRatesWhereTheDenominatorIsZero() {
        organizerAccess();
        when(reports.ticketsByStatus(event.getId())).thenReturn(List.of());
        when(reports.paymentsByStatus(event.getId())).thenReturn(List.of());
        when(reports.attendance(event.getId())).thenReturn(0L);

        EventSummaryResponse summary = service.eventSummary(event.getId(), organizer);

        assertEquals(0, summary.ticketsSold());
        assertEquals(0, summary.ticketsPending());
        assertEquals(new BigDecimal("0.00"), summary.revenue());
        assertNull(summary.averageTicketPrice());
        assertEquals(0, summary.payments().approved());
        assertNull(summary.approvalRate());
        assertEquals(0, summary.attendance());
        assertNull(summary.attendanceRate());
        // La ocupación sí tiene denominador (capacidad 100): es 0, no nula.
        assertEquals(new BigDecimal("0.0000"), summary.occupancyRate());
    }

    @Test
    void eventSummary_ForAVirtualEvent_HasNoCapacityNorOccupancy() {
        VirtualEvent virtual = new VirtualEvent("Webinar", "Desc", LocalDateTime.of(2026, 3, 20, 18, 0),
                new BigDecimal("40.00"), "https://meet.example.com", "Meet", null);
        virtual.setId(UUID.randomUUID());
        virtual.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(virtual.getId())).thenReturn(Optional.of(virtual));
        when(authorization.isOrganizer(virtual, organizer)).thenReturn(true);
        when(reports.ticketsByStatus(virtual.getId()))
                .thenReturn(List.of(new TicketStatusCount(TicketStatus.ACTIVE, 4)));
        when(reports.paymentsByStatus(virtual.getId()))
                .thenReturn(List.of(payments(PaymentStatus.APPROVED, 4, "160.00")));
        when(reports.attendance(virtual.getId())).thenReturn(3L);

        EventSummaryResponse summary = service.eventSummary(virtual.getId(), organizer);

        assertEquals(EventType.VIRTUAL, summary.eventType());
        assertNull(summary.capacity());
        assertNull(summary.occupancyRate());
        assertEquals(new BigDecimal("40.00"), summary.averageTicketPrice());
        assertEquals(new BigDecimal("0.7500"), summary.attendanceRate());
    }

    @Test
    void eventSummary_AUsedTicketCountsAsSoldAndAsAttendee() {
        organizerAccess();
        stubTickets(0, 1, 0, 0);
        stubPayments(payments(PaymentStatus.APPROVED, 1, "50.00"));
        when(reports.attendance(event.getId())).thenReturn(1L);

        EventSummaryResponse summary = service.eventSummary(event.getId(), organizer);

        assertEquals(1, summary.ticketsSold());
        assertEquals(1, summary.attendance());
        assertEquals(new BigDecimal("1.0000"), summary.attendanceRate());
    }

    @Test
    void eventSummary_WhenThereAreOnlyRejectedPayments_ApprovalRateIsZeroNotNull() {
        organizerAccess();
        stubTickets(0, 0, 0, 2);
        stubPayments(payments(PaymentStatus.REJECTED, 2, "100.00"));
        when(reports.attendance(event.getId())).thenReturn(0L);

        EventSummaryResponse summary = service.eventSummary(event.getId(), organizer);

        assertEquals(new BigDecimal("0.0000"), summary.approvalRate());
        assertEquals(new BigDecimal("0.00"), summary.revenue());
    }

    @Test
    void eventSummary_WithAnInPersonEventOfZeroCapacity_HasNoOccupancy() {
        organizerAccess();
        event.setCapacity(0);
        stubTickets(1, 0, 0, 0);
        stubPayments(payments(PaymentStatus.APPROVED, 1, "50.00"));
        when(reports.attendance(event.getId())).thenReturn(0L);

        assertNull(service.eventSummary(event.getId(), organizer).occupancyRate());
    }

    @Test
    void eventSummary_RoundsMoneyAndRatesHalfUp() {
        organizerAccess();
        stubTickets(3, 0, 0, 0);
        stubPayments(payments(PaymentStatus.APPROVED, 2, "100.00"), payments(PaymentStatus.REJECTED, 1, "10.00"));
        when(reports.attendance(event.getId())).thenReturn(2L);

        EventSummaryResponse summary = service.eventSummary(event.getId(), organizer);

        assertEquals(new BigDecimal("33.33"), summary.averageTicketPrice());
        assertEquals(new BigDecimal("0.6667"), summary.approvalRate());
        assertEquals(new BigDecimal("0.6667"), summary.attendanceRate());
    }

    @Test
    void eventSummary_ByANonOrganizer_ThrowsForbiddenWithoutQueryingTheAggregates() {
        when(authorization.isOrganizer(event, organizer)).thenReturn(false);

        assertThrows(ReportAccessDeniedException.class, () -> service.eventSummary(event.getId(), organizer));

        verifyNoInteractions(reports);
    }

    @Test
    void eventSummary_ByASuperuser_ReadsAnyEventWithoutBeingTheOrganizer() {
        User admin = new User("Ana", "ana@example.com", "hash", LocalDate.of(1990, 1, 1));
        admin.setSystemRole(SystemRole.ADMIN);
        stubTickets(1, 0, 0, 0);
        stubPayments(payments(PaymentStatus.APPROVED, 1, "50.00"));
        when(reports.attendance(event.getId())).thenReturn(0L);

        EventSummaryResponse summary = service.eventSummary(event.getId(), admin);

        assertEquals(new BigDecimal("50.00"), summary.revenue());
        verify(authorization, never()).isOrganizer(any(Event.class), any(User.class));
    }

    @Test
    void eventSummary_WhenTheEventDoesNotExist_ThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(eventRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> service.eventSummary(missing, organizer));

        verifyNoInteractions(reports, authorization);
    }
}
