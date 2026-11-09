package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gcrel.tfbackevent.access.service.EventAuthorizationService;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.report.dto.response.OrganizerSummaryResponse;
import org.gcrel.tfbackevent.report.repository.ReportRepository;
import org.gcrel.tfbackevent.report.repository.dto.EventRevenue;
import org.gcrel.tfbackevent.report.repository.dto.EventStatusCount;
import org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals;
import org.gcrel.tfbackevent.report.repository.dto.TicketStatusCount;
import org.gcrel.tfbackevent.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReportOrganizerServiceTest {
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventAuthorizationService authorization;
    @Mock
    private ReportRepository reports;
    @Mock
    private Clock clock;

    private ReportService service;
    private User organizer;
    private final List<UUID> eventIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new ReportService(eventRepository, authorization, reports, clock);
        organizer = new User("Org", "org@example.com", "hash", LocalDate.of(1990, 1, 1));
        organizer.setId(UUID.randomUUID());
    }

    @Test
    void organizerSummary_AddsUpAllTheEventsAndLeavesPendingAndRejectedOutOfRevenue() {
        when(reports.organizedEventIds(organizer.getId())).thenReturn(eventIds);
        when(reports.ticketsByStatusIn(eventIds)).thenReturn(List.of(
                new TicketStatusCount(TicketStatus.ACTIVE, 6), new TicketStatusCount(TicketStatus.USED, 3),
                new TicketStatusCount(TicketStatus.PENDING, 2), new TicketStatusCount(TicketStatus.CANCELLED, 1)));
        when(reports.paymentsByStatusIn(eventIds)).thenReturn(List.of(
                new PaymentStatusTotals(PaymentStatus.APPROVED, 4, new BigDecimal("340")),
                new PaymentStatusTotals(PaymentStatus.REJECTED, 1, new BigDecimal("50.00")),
                new PaymentStatusTotals(PaymentStatus.PENDING, 1, new BigDecimal("100.00"))));
        when(reports.eventsByStatusIn(eventIds)).thenReturn(List.of(
                new EventStatusCount(EventStatus.PUBLISHED, 2), new EventStatusCount(EventStatus.DRAFT, 1)));
        when(reports.topEventsByRevenueIn(eq(eventIds), any(Pageable.class))).thenReturn(List.of());

        OrganizerSummaryResponse summary = service.organizerSummary(organizer);

        assertEquals(9, summary.ticketsSold());
        assertEquals(2, summary.ticketsPending());
        assertEquals(new BigDecimal("340.00"), summary.revenue());
        assertEquals(4, summary.payments().approved());
        assertEquals(1, summary.payments().rejected());
        assertEquals(1, summary.payments().pending());
    }

    @Test
    void organizerSummary_ReportsEveryEventStatusWithZeroWhereThereAreNone() {
        stubEmptyAggregates();
        when(reports.eventsByStatusIn(eventIds)).thenReturn(List.of(new EventStatusCount(EventStatus.PUBLISHED, 2)));

        Map<EventStatus, Long> byStatus = service.organizerSummary(organizer).eventsByStatus();

        assertEquals(Map.of(EventStatus.DRAFT, 0L, EventStatus.PUBLISHED, 2L, EventStatus.CANCELLED, 0L,
                EventStatus.FINISHED, 0L), byStatus);
    }

    @Test
    void organizerSummary_AsksForTheTopFiveAndKeepsTheQueryOrder() {
        stubEmptyAggregates();
        UUID best = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(reports.topEventsByRevenueIn(eq(eventIds), any(Pageable.class))).thenReturn(List.of(
                new EventRevenue(best, "Grande", new BigDecimal("300"), 6),
                new EventRevenue(second, "Chico", new BigDecimal("40.5"), 1)));

        OrganizerSummaryResponse summary = service.organizerSummary(organizer);

        ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
        verify(reports).topEventsByRevenueIn(eq(eventIds), limit.capture());
        assertEquals(5, limit.getValue().getPageSize());
        assertEquals(0, limit.getValue().getPageNumber());
        assertEquals(List.of(best, second), summary.topEvents().stream().map(t -> t.eventId()).toList());
        assertEquals(new BigDecimal("300.00"), summary.topEvents().get(0).revenue());
        assertEquals(new BigDecimal("40.50"), summary.topEvents().get(1).revenue());
        assertEquals(6, summary.topEvents().get(0).paidTickets());
    }

    @Test
    void organizerSummary_WithoutEvents_ReturnsZerosWithoutQueryingTheAggregates() {
        when(reports.organizedEventIds(organizer.getId())).thenReturn(List.of());

        OrganizerSummaryResponse summary = service.organizerSummary(organizer);

        assertEquals(0, summary.ticketsSold());
        assertEquals(0, summary.ticketsPending());
        assertEquals(new BigDecimal("0.00"), summary.revenue());
        assertEquals(0, summary.payments().approved());
        assertEquals(4, summary.eventsByStatus().size());
        assertEquals(0L, summary.eventsByStatus().get(EventStatus.PUBLISHED));
        assertEquals(List.of(), summary.topEvents());
        verify(reports).organizedEventIds(organizer.getId());
        verifyNoMoreInteractions(reports);
    }

    private void stubEmptyAggregates() {
        when(reports.organizedEventIds(organizer.getId())).thenReturn(eventIds);
        when(reports.ticketsByStatusIn(eventIds)).thenReturn(List.of());
        when(reports.paymentsByStatusIn(eventIds)).thenReturn(List.of());
        lenient().when(reports.eventsByStatusIn(eventIds)).thenReturn(List.of());
        lenient().when(reports.topEventsByRevenueIn(eq(eventIds), any(Pageable.class))).thenReturn(List.of());
    }
}
