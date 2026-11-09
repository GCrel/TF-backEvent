package org.gcrel.tfbackevent.unit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.service.AdminEventService;
import org.gcrel.tfbackevent.admin.service.AuditService;
import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.event.exception.EventHasTicketsException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminEventServiceTest {
    @Mock
    private EventService eventService;
    @Mock
    private EventParticipationRepository participationRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AdminEventService service;

    private User actor;
    private InPersonEvent event;
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        actor = new User("Ana", "ana@example.com", "hash", LocalDate.of(1990, 1, 1));
        event = new InPersonEvent("Conf", "Desc", LocalDateTime.now().plusDays(3), new BigDecimal("10.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 10);
    }

    @Test
    void cancel_AuditsAndNotifiesTheOrganizer() {
        User organizer = new User("Org", "org@example.com", "hash", LocalDate.of(1990, 1, 1));
        when(eventService.cancelEventAsAdmin(eventId)).thenReturn(event);
        when(participationRepository.findByEventAndId_EventRole(event, EventRole.ORGANIZER))
                .thenReturn(Optional.of(new EventParticipation(organizer, event, EventRole.ORGANIZER)));

        service.cancel(actor, eventId);

        verify(auditService).record(actor, AuditAction.EVENT_CANCELLED, AuditTargetType.EVENT, eventId, "Conf");
        verify(emailService).sendNotice(eq("org@example.com"), anyString(), anyString());
    }

    @Test
    void cancel_WhenEventHasTickets_RecordsAndNotifiesNothing() {
        when(eventService.cancelEventAsAdmin(eventId)).thenThrow(new EventHasTicketsException());

        assertThrows(EventHasTicketsException.class, () -> service.cancel(actor, eventId));

        verify(auditService, never()).record(any(), any(), any(), any(), any());
        verify(emailService, never()).sendNotice(any(), any(), any());
    }

    @Test
    void cancel_WithoutOrganizer_StillCancelsAndAudits() {
        when(eventService.cancelEventAsAdmin(eventId)).thenReturn(event);
        when(participationRepository.findByEventAndId_EventRole(event, EventRole.ORGANIZER))
                .thenReturn(Optional.empty());

        service.cancel(actor, eventId);

        verify(auditService).record(actor, AuditAction.EVENT_CANCELLED, AuditTargetType.EVENT, eventId, "Conf");
        verify(emailService, never()).sendNotice(any(), any(), any());
    }
}
