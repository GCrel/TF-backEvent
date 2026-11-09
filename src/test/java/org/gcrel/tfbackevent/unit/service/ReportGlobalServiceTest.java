package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gcrel.tfbackevent.access.service.EventAuthorizationService;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.report.dto.response.GlobalStatsResponse;
import org.gcrel.tfbackevent.report.repository.ReportRepository;
import org.gcrel.tfbackevent.report.repository.dto.EventRevenue;
import org.gcrel.tfbackevent.report.repository.dto.EventStatusCount;
import org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals;
import org.gcrel.tfbackevent.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReportGlobalServiceTest {
    private static final LocalDate FROM = LocalDate.of(2026, 3, 11);
    private static final LocalDate TO = LocalDate.of(2026, 3, 12);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventAuthorizationService authorization;
    @Mock
    private ReportRepository reports;
    @Mock
    private Clock clock;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(eventRepository, authorization, reports, clock);
        lenient().when(reports.paymentsByStatusBetween(any(), any())).thenReturn(List.of());
        lenient().when(reports.topEventsByRevenueBetween(any(), any(), any(Pageable.class))).thenReturn(List.of());
        lenient().when(reports.eventsByStatus()).thenReturn(List.of());
    }

    @Test
    void globalOverview_AddsPaidAndFreeTicketsAndTakesRevenueFromApprovedPaymentsOnly() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 11, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 13, 0, 0);
        when(reports.paymentsByStatusBetween(start, end)).thenReturn(List.of(
                new PaymentStatusTotals(PaymentStatus.APPROVED, 4, new BigDecimal("265")),
                new PaymentStatusTotals(PaymentStatus.REJECTED, 1, new BigDecimal("50.00"))));
        when(reports.paidTicketsBetween(start, end)).thenReturn(6L);
        when(reports.freeTicketsBetween(start, end)).thenReturn(2L);
        when(reports.eventsByStatus()).thenReturn(List.of(
                new EventStatusCount(EventStatus.PUBLISHED, 3), new EventStatusCount(EventStatus.DRAFT, 1)));
        when(reports.countUsers()).thenReturn(8L);
        when(reports.countUsersCreatedBetween(start, end)).thenReturn(2L);

        GlobalStatsResponse stats = service.globalOverview(FROM, TO);

        assertEquals(FROM, stats.from());
        assertEquals(TO, stats.to());
        assertEquals(8, stats.ticketsSold());
        assertEquals(new BigDecimal("265.00"), stats.revenue());
        assertEquals(4, stats.payments().approved());
        assertEquals(1, stats.payments().rejected());
        assertEquals(0, stats.payments().pending());
        assertEquals(Map.of(EventStatus.PUBLISHED, 3L, EventStatus.DRAFT, 1L, EventStatus.CANCELLED, 0L,
                EventStatus.FINISHED, 0L), stats.eventsByStatus());
        assertEquals(8, stats.users().total());
        assertEquals(2, stats.users().newInPeriod());
    }

    @Test
    void globalOverview_TurnsTheInclusiveDatesIntoAHalfOpenRangeEndingTheDayAfter() {
        service.globalOverview(FROM, TO);

        verify(reports).paymentsByStatusBetween(LocalDateTime.of(2026, 3, 11, 0, 0), LocalDateTime.of(2026, 3, 13, 0, 0));
    }

    @Test
    void globalOverview_OfASingleDay_CoversThatWholeDay() {
        service.globalOverview(FROM, FROM);

        verify(reports).paymentsByStatusBetween(LocalDateTime.of(2026, 3, 11, 0, 0), LocalDateTime.of(2026, 3, 12, 0, 0));
    }

    @Test
    void globalOverview_AsksForTheTopTenAndKeepsTheQueryOrder() {
        UUID best = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(reports.topEventsByRevenueBetween(any(), any(), any(Pageable.class))).thenReturn(List.of(
                new EventRevenue(best, "Grande", new BigDecimal("200"), 4), new EventRevenue(second, "Chico", new BigDecimal("40.5"), 1)));

        GlobalStatsResponse stats = service.globalOverview(FROM, TO);

        ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
        verify(reports).topEventsByRevenueBetween(any(), any(), limit.capture());
        assertEquals(10, limit.getValue().getPageSize());
        assertEquals(List.of(best, second), stats.topEvents().stream().map(t -> t.eventId()).toList());
        assertEquals(new BigDecimal("200.00"), stats.topEvents().get(0).revenue());
        assertEquals(new BigDecimal("40.50"), stats.topEvents().get(1).revenue());
    }

    @Test
    void globalOverview_WithoutActivity_ReturnsZeros() {
        GlobalStatsResponse stats = service.globalOverview(FROM, TO);

        assertEquals(0, stats.ticketsSold());
        assertEquals(new BigDecimal("0.00"), stats.revenue());
        assertEquals(0, stats.payments().approved());
        assertEquals(List.of(), stats.topEvents());
    }

    @Test
    void globalOverview_AcceptsAFullYearAndRejectsOneDayMore() {
        service.globalOverview(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

        assertThrows(BadRequestException.class,
                () -> service.globalOverview(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)));
    }

    @Test
    void globalOverview_WhenFromIsAfterTo_ThrowsBadRequestWithoutQuerying() {
        assertThrows(BadRequestException.class, () -> service.globalOverview(TO, FROM));

        verifyNoInteractions(reports);
    }
}
