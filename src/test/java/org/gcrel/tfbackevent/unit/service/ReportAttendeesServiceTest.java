package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.access.service.EventAuthorizationService;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.report.dto.response.AttendeeResponse;
import org.gcrel.tfbackevent.report.exception.ReportAccessDeniedException;
import org.gcrel.tfbackevent.report.repository.ReportRepository;
import org.gcrel.tfbackevent.report.repository.dto.AttendeeRow;
import org.gcrel.tfbackevent.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ReportAttendeesServiceTest {
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
    private InPersonEvent event;

    @BeforeEach
    void setUp() {
        service = new ReportService(eventRepository, authorization, reports, clock);
        organizer = new User("Org", "org@example.com", "hash", LocalDate.of(1990, 1, 1));
        event = new InPersonEvent("Conf", "Desc", LocalDateTime.of(2026, 3, 20, 18, 0), new BigDecimal("50.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        event.setId(UUID.randomUUID());
        event.setStatus(EventStatus.PUBLISHED);
        lenient().when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        lenient().when(authorization.isOrganizer(event, organizer)).thenReturn(true);
    }

    private static AttendeeRow row(String name, TicketStatus status, LocalDateTime accessedAt) {
        return new AttendeeRow(name, status, LocalDateTime.of(2026, 3, 10, 9, 5, 7), accessedAt);
    }

    @Test
    void attendees_MapsTheRowsAndIgnoresTheClientSort() {
        Pageable clientPage = PageRequest.of(2, 10, Sort.by("name").descending());
        when(reports.attendees(eq(event.getId()), any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(row("Ana", TicketStatus.USED, LocalDateTime.of(2026, 3, 20, 9, 15))), PageRequest.of(2, 10), 21));

        Page<AttendeeResponse> page = service.attendees(event.getId(), organizer, clientPage);

        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(reports).attendees(eq(event.getId()), asked.capture());
        assertEquals(2, asked.getValue().getPageNumber());
        assertEquals(10, asked.getValue().getPageSize());
        assertTrue(asked.getValue().getSort().isUnsorted());
        assertEquals(21, page.getTotalElements());
        assertEquals("Ana", page.getContent().get(0).name());
        assertEquals(TicketStatus.USED, page.getContent().get(0).status());
    }

    @Test
    void attendeesCsv_StartsWithTheBomAndHeaderAndWritesOneLinePerAttendee() {
        when(reports.attendees(eq(event.getId()), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                row("Ana García", TicketStatus.USED, LocalDateTime.of(2026, 3, 20, 9, 15)),
                row("Beto", TicketStatus.ACTIVE, null))));

        String csv = service.attendeesCsv(event.getId(), organizer);

        assertEquals("﻿Nombre,Estado,Fecha de compra,Fecha de acceso\r\n"
                + "Ana García,Usada,2026-03-10 09:05:07,2026-03-20 09:15:00\r\n"
                + "Beto,Activa,2026-03-10 09:05:07,\r\n", csv);
    }

    @Test
    void attendeesCsv_NeutralizesNamesThatLookLikeFormulas() {
        when(reports.attendees(eq(event.getId()), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                row("=HYPERLINK(\"http://evil\")", TicketStatus.ACTIVE, null))));

        String csv = service.attendeesCsv(event.getId(), organizer);

        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://evil\"\")\",Activa"), csv);
    }

    @Test
    void attendeesCsv_ReadsEveryPageUntilTheLastOne() {
        List<AttendeeRow> first = java.util.Collections.nCopies(500, row("Ana", TicketStatus.ACTIVE, null));
        List<AttendeeRow> second = List.of(row("Beto", TicketStatus.ACTIVE, null));
        when(reports.attendees(event.getId(), PageRequest.of(0, 500)))
                .thenReturn(new PageImpl<>(first, PageRequest.of(0, 500), 501));
        when(reports.attendees(event.getId(), PageRequest.of(1, 500)))
                .thenReturn(new PageImpl<>(second, PageRequest.of(1, 500), 501));

        String csv = service.attendeesCsv(event.getId(), organizer);

        verify(reports, times(2)).attendees(eq(event.getId()), any(Pageable.class));
        assertEquals(502, csv.split("\r\n").length);
        assertTrue(csv.endsWith("Beto,Activa,2026-03-10 09:05:07,\r\n"));
    }

    @Test
    void attendeesCsv_OfAnEventWithoutAttendees_HasOnlyTheHeader() {
        when(reports.attendees(eq(event.getId()), any(Pageable.class))).thenReturn(Page.empty());

        assertEquals("﻿Nombre,Estado,Fecha de compra,Fecha de acceso\r\n",
                service.attendeesCsv(event.getId(), organizer));
    }

    @Test
    void attendees_ByANonOrganizer_ThrowForbiddenWithoutQueryingThem() {
        User stranger = new User("Otro", "otro@example.com", "hash", LocalDate.of(1990, 1, 1));
        when(authorization.isOrganizer(event, stranger)).thenReturn(false);

        assertThrows(ReportAccessDeniedException.class,
                () -> service.attendees(event.getId(), stranger, PageRequest.of(0, 20)));
        assertThrows(ReportAccessDeniedException.class, () -> service.attendeesCsv(event.getId(), stranger));

        verifyNoInteractions(reports);
    }

    @Test
    void attendees_WhenTheEventDoesNotExist_ThrowNotFound() {
        UUID missing = UUID.randomUUID();
        when(eventRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> service.attendees(missing, organizer, PageRequest.of(0, 20)));
        assertThrows(EventNotFoundException.class, () -> service.attendeesCsv(missing, organizer));
    }
}
