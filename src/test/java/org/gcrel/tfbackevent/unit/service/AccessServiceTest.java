package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.access.dto.request.ValidateQrRequest;
import org.gcrel.tfbackevent.access.dto.response.VirtualAccessResponse;
import org.gcrel.tfbackevent.access.exception.AccessWindowClosedException;
import org.gcrel.tfbackevent.access.exception.TicketAlreadyUsedException;
import org.gcrel.tfbackevent.access.exception.TicketEventMismatchException;
import org.gcrel.tfbackevent.access.exception.TicketNotValidException;
import org.gcrel.tfbackevent.access.service.AccessService;
import org.gcrel.tfbackevent.access.service.EventAuthorizationService;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.config.EventWindowProperties;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.exception.InvalidEventTypeException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.repository.dto.TicketAccessState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {
    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private EventAuthorizationService authorization;

    @Mock
    private EventWindowProperties windowProperties;

    @InjectMocks
    private AccessService accessService;

    private User attendee;
    private User staff;
    private InPersonEvent event;

    @BeforeEach
    void setUp() {
        attendee = user("attendee@example.com");
        staff = user("staff@example.com");
        event = new InPersonEvent(
                "Conference",
                "Description",
                LocalDateTime.now().plusMinutes(30),
                new BigDecimal("25.00"),
                "Main St 123",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                100);
        event.setId(UUID.randomUUID());
        event.setStatus(EventStatus.PUBLISHED);
        org.mockito.Mockito.lenient().when(windowProperties.beforeHours()).thenReturn(3L);
        org.mockito.Mockito.lenient().when(windowProperties.afterHours()).thenReturn(12L);
    }

    @Test
    void validateQr_MarksActiveTicketAsUsed() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, event, TicketStatus.ACTIVE);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(authorization.isStaff(event, staff)).thenReturn(true);
        when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.markAsUsedIfActive(
                eq(ticketId), any(), eq(staff), eq(TicketStatus.ACTIVE), eq(TicketStatus.USED)))
                .thenReturn(1);

        var response = accessService.validateQr(
                new ValidateQrRequest(event.getId(), ticketId.toString()), staff);

        assertEquals(ticketId, response.ticketId());
        assertEquals("Test User", response.attendeeName());
        assertEquals("Conference", response.eventTitle());
        verify(ticketRepository).markAsUsedIfActive(
                eq(ticketId), any(), eq(staff), eq(TicketStatus.ACTIVE), eq(TicketStatus.USED));
        verify(ticketRepository, never()).findAccessStateById(ticketId);
    }

    @Test
    void validateQr_WhenStaffIsUnauthorized_DoesNotLookupTicket() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(authorization.isStaff(event, staff)).thenReturn(false);

        assertThrows(UnauthorizedEventAccessException.class, () -> accessService.validateQr(
                new ValidateQrRequest(event.getId(), UUID.randomUUID().toString()), staff));

        verify(ticketRepository, never()).findWithEventAndAttendeeById(any());
    }

    @Test
    void validateQr_WhenOutsideWindow_ThrowsAndDoesNotUpdate() {
        event.setDateTime(LocalDateTime.now().minusHours(12).minusMinutes(1));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(authorization.isStaff(event, staff)).thenReturn(true);

        assertThrows(AccessWindowClosedException.class, () -> accessService.validateQr(
                new ValidateQrRequest(event.getId(), UUID.randomUUID().toString()), staff));

        verify(ticketRepository, never()).markAsUsedIfActive(any(), any(), any(), any(), any());
    }

    @Test
    void validateQr_WhenTicketBelongsToAnotherEvent_ThrowsMismatch() {
        UUID ticketId = UUID.randomUUID();
        InPersonEvent otherEvent = new InPersonEvent(
                "Other", "Description", LocalDateTime.now().plusMinutes(30),
                new BigDecimal("25.00"), "Other address",
                new BigDecimal("-34.60"), new BigDecimal("-58.38"), 50);
        otherEvent.setId(UUID.randomUUID());
        Ticket ticket = ticket(ticketId, otherEvent, TicketStatus.ACTIVE);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(authorization.isStaff(event, staff)).thenReturn(true);
        when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(TicketEventMismatchException.class, () -> accessService.validateQr(
                new ValidateQrRequest(event.getId(), ticketId.toString()), staff));

        verify(ticketRepository, never()).markAsUsedIfActive(any(), any(), any(), any(), any());
    }

    @Test
    void validateQr_WhenAtomicUpdateFailsForUsedTicket_ReportsAlreadyUsed() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, event, TicketStatus.USED);
        LocalDateTime accessedAt = LocalDateTime.now().minusMinutes(2);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(authorization.isStaff(event, staff)).thenReturn(true);
        when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.markAsUsedIfActive(
                eq(ticketId), any(), eq(staff), eq(TicketStatus.ACTIVE), eq(TicketStatus.USED)))
                .thenReturn(0);
        when(ticketRepository.findAccessStateById(ticketId))
                .thenReturn(Optional.of(new TicketAccessState(TicketStatus.USED, accessedAt)));

        TicketAlreadyUsedException exception = assertThrows(TicketAlreadyUsedException.class,
                () -> accessService.validateQr(
                        new ValidateQrRequest(event.getId(), ticketId.toString()), staff));

        assertEquals(
                "Ticket fue usado: "
                        + accessedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                exception.getMessage());
    }

    @Test
    void validateQr_WhenBeforeWindow_ThrowsAndDoesNotLookupTicket() {
        event.setDateTime(LocalDateTime.now().plusHours(3).plusMinutes(1));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(authorization.isStaff(event, staff)).thenReturn(true);

        assertThrows(AccessWindowClosedException.class, () -> accessService.validateQr(
                new ValidateQrRequest(event.getId(), UUID.randomUUID().toString()), staff));
        verify(ticketRepository, never()).findWithEventAndAttendeeById(any());
    }

    @Test
    void validateQr_WhenEventIsVirtual_ThrowsInvalidEventType() {
        VirtualEvent virtual = new VirtualEvent(
                "Online", "Description", LocalDateTime.now().plusMinutes(30),
                new BigDecimal("10"), "https://meet.example.com", "Meet", "Instructions");
        virtual.setId(event.getId());
        virtual.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(virtual));
        when(authorization.isStaff(virtual, staff)).thenReturn(true);

        assertThrows(InvalidEventTypeException.class, () -> accessService.validateQr(
                new ValidateQrRequest(event.getId(), UUID.randomUUID().toString()), staff));
    }

    @Test
    void validateQr_WhenEventIsNotPublished_ThrowsNotAvailable() {
        event.setStatus(EventStatus.DRAFT);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(authorization.isStaff(event, staff)).thenReturn(true);

        assertThrows(org.gcrel.tfbackevent.event.exception.EventNotAvailableException.class,
                () -> accessService.validateQr(
                        new ValidateQrRequest(event.getId(), UUID.randomUUID().toString()), staff));
    }

    @Test
    void validateQr_WhenQrIsNotUuid_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> accessService.validateQr(
                new ValidateQrRequest(event.getId(), "not-a-uuid"), staff));
        verify(eventRepository, never()).findById(any());
    }

    @Test
    void validateQr_WhenTicketIsPendingOrCancelled_ThrowsTicketNotValid() {
        for (TicketStatus status : new TicketStatus[] { TicketStatus.PENDING, TicketStatus.CANCELLED }) {
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = ticket(ticketId, event, status);
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(authorization.isStaff(event, staff)).thenReturn(true);
            when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.of(ticket));
            when(ticketRepository.markAsUsedIfActive(
                    eq(ticketId), any(), eq(staff), eq(TicketStatus.ACTIVE), eq(TicketStatus.USED)))
                    .thenReturn(0);
            when(ticketRepository.findAccessStateById(ticketId))
                    .thenReturn(Optional.of(new TicketAccessState(status, null)));

            assertThrows(TicketNotValidException.class, () -> accessService.validateQr(
                    new ValidateQrRequest(event.getId(), ticketId.toString()), staff));
        }
    }

    @Test
    void validateQr_WhenEventOrTicketDoesNotExist_ThrowsNotFound() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.empty());
        assertThrows(EventNotFoundException.class, () -> accessService.validateQr(
                new ValidateQrRequest(event.getId(), UUID.randomUUID().toString()), staff));

        UUID ticketId = UUID.randomUUID();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(authorization.isStaff(event, staff)).thenReturn(true);
        when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.empty());
        assertThrows(TicketNotFoundException.class, () -> accessService.validateQr(
                new ValidateQrRequest(event.getId(), ticketId.toString()), staff));
    }

    @Test
    void getVirtualAccess_ReturnsCredentialsAndRegistersFirstAccess() {
        UUID ticketId = UUID.randomUUID();
        VirtualEvent virtualEvent = new VirtualEvent(
                "Online Conference", "Description",
                LocalDateTime.now().plusDays(1), new BigDecimal("15.00"),
                "https://meet.example.com/room", "Meet", "Use the event link");
        virtualEvent.setId(UUID.randomUUID());
        Ticket ticket = ticket(ticketId, virtualEvent, TicketStatus.ACTIVE);
        when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.of(ticket));

        VirtualAccessResponse response = accessService.getVirtualAccess(ticketId, attendee);

        assertEquals("Online Conference", response.eventTitle());
        assertEquals("Meet", response.platform());
        assertEquals("https://meet.example.com/room", response.accessLink());
        verify(ticketRepository).markFirstAccess(eq(ticketId), any());
    }

    @Test
    void getVirtualAccess_WhenUserIsNotAttendee_ThrowsBeforeReturningCredentials() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, event, TicketStatus.ACTIVE);
        when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(
                org.gcrel.tfbackevent.payment.exception.UnauthorizedTicketAccessException.class,
                () -> accessService.getVirtualAccess(ticketId, staff));

        verify(ticketRepository, never()).markFirstAccess(any(), any());
    }

    @Test
    void getVirtualAccess_WhenTicketIsNotActive_ThrowsConflict() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, new VirtualEvent(
                "Online", "Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("10"), "https://meet.example.com", "Meet", "Instructions"),
                TicketStatus.PENDING);
        when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(TicketNotActiveException.class,
                () -> accessService.getVirtualAccess(ticketId, attendee));
    }

    @Test
    void getVirtualAccess_WhenEventIsInPerson_ThrowsInvalidEventType() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, event, TicketStatus.ACTIVE);
        when(ticketRepository.findWithEventAndAttendeeById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(InvalidEventTypeException.class,
                () -> accessService.getVirtualAccess(ticketId, attendee));
    }

    private Ticket ticket(UUID id, org.gcrel.tfbackevent.model.Event event, TicketStatus status) {
        TicketOrder order = new TicketOrder(
                attendee, event, 1, event.getBasePrice(), LocalDateTime.now().minusHours(1));
        Ticket ticket = new Ticket(order);
        ticket.setId(id);
        ticket.setStatus(status);
        return ticket;
    }

    private User user(String email) {
        User user = new User("Test User", email, "password", LocalDate.of(1990, 1, 1));
        user.setId(UUID.randomUUID());
        return user;
    }
}
