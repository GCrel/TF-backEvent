package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.config.EventWindowProperties;
import org.gcrel.tfbackevent.event.dto.request.CreateEventRequest;
import org.gcrel.tfbackevent.event.dto.request.UpdateEventRequest;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.exception.EventHasSalesException;
import org.gcrel.tfbackevent.event.exception.EventHasTicketsException;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.exception.EventNotModifiableException;
import org.gcrel.tfbackevent.event.exception.EventNotPublishableException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.repository.dto.EventTicketCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventParticipationRepository eventParticipationRepository;

    @Mock
    private OrganizerMpAccountRepository mpAccountRepository;

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private EventWindowProperties eventWindowProperties;

    @InjectMocks
    private EventService eventService;

    private User organizer;
    private final LocalDateTime eventDateTime = LocalDateTime.of(2026, 9, 30, 19, 30);

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(eventWindowProperties.afterHours()).thenReturn(12L);
        organizer = new User(
                "John Doe",
                "test@example.com",
                "encoded_password",
                LocalDate.of(1990, 1, 1));
    }

    @Test
    void createEvent_InPerson_ShouldPersistEventAndOrganizerParticipation() {
        CreateEventRequest request = new CreateEventRequest(
                "Tech Meetup",
                "Evento de tecnología",
                eventDateTime,
                new BigDecimal("150.00"),
                EventType.IN_PERSON,
                "Av. Siempre Viva 123",
                new BigDecimal("-34.6037"),
                new BigDecimal("-58.3816"),
                200,
                null,
                null,
                null);

        EventResponse response = eventService.createEvent(request, organizer);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        verify(eventParticipationRepository).save(any(EventParticipation.class));

        Event savedEvent = eventCaptor.getValue();
        assertInstanceOf(InPersonEvent.class, savedEvent);
        assertEquals("Tech Meetup", savedEvent.getTitle());
        assertEquals("IN_PERSON", response.eventType());
        assertEquals("Av. Siempre Viva 123", response.address());
        assertEquals(200, response.capacity());
    }

    @Test
    void createEvent_Virtual_ShouldPersistVirtualEvent() {
        CreateEventRequest request = new CreateEventRequest(
                "Webinar",
                "Sesión online",
                eventDateTime,
                new BigDecimal("50.00"),
                EventType.VIRTUAL,
                null,
                null,
                null,
                null,
                "https://meet.example.com/link",
                "Zoom",
                "Ingresá usando el link");

        EventResponse response = eventService.createEvent(request, organizer);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertInstanceOf(VirtualEvent.class, savedEvent);
        assertEquals("Zoom", ((VirtualEvent) savedEvent).getPlatform());
        assertEquals("VIRTUAL", response.eventType());
        assertEquals("Zoom", response.platform());
    }

    @Test
    void createEvent_VirtualWithoutPlatform_ShouldThrowBadRequest() {
        CreateEventRequest request = new CreateEventRequest(
                "Webinar",
                "Sesión online",
                eventDateTime,
                new BigDecimal("50.00"),
                EventType.VIRTUAL,
                null,
                null,
                null,
                null,
                "https://meet.example.com/link",
                null,
                "Ingresá usando el link");

        assertThrows(BadRequestException.class, () -> eventService.createEvent(request, organizer));

        verify(eventRepository, never()).save(any());
        verify(eventParticipationRepository, never()).save(any());
    }

    @Test
    void updateEvent_WhenOrganizerAndEditable_ShouldUpdateValues() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Event old",
                "Old description",
                eventDateTime,
                new BigDecimal("100.00"),
                "Old address",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                50);
        existingEvent.setStatus(EventStatus.DRAFT);

        UpdateEventRequest request = new UpdateEventRequest(
                "Event updated",
                "Updated description",
                eventDateTime.plusHours(2),
                new BigDecimal("180.00"),
                "New address",
                new BigDecimal("-34.61"),
                new BigDecimal("-58.39"),
                120,
                null,
                null,
                null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER))
                .thenReturn(true);

        EventResponse response = eventService.updateEvent(eventId, request, organizer);

        assertEquals("Event updated", response.title());
        assertEquals("Updated description", response.description());
        assertEquals(new BigDecimal("180.00"), response.basePrice());
        assertEquals("New address", response.address());
        assertEquals(120, response.capacity());
        verify(eventRepository).save(existingEvent);
    }

    @Test
    void updateEvent_InPersonWithoutCapacity_ShouldThrowBadRequest() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Event old",
                "Old description",
                eventDateTime,
                new BigDecimal("100.00"),
                "Old address",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                50);
        existingEvent.setStatus(EventStatus.DRAFT);

        UpdateEventRequest request = new UpdateEventRequest(
                "Event updated",
                "Updated description",
                eventDateTime.plusHours(2),
                new BigDecimal("180.00"),
                "New address",
                new BigDecimal("-34.61"),
                new BigDecimal("-58.39"),
                null,
                null,
                null,
                null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER))
                .thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> eventService.updateEvent(eventId, request, organizer));

        verify(eventRepository, never()).save(existingEvent);
    }

    @Test
    void updateEvent_WhenUserIsNotOrganizer_ShouldThrowUnauthorizedEventAccessException() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Event old",
                "Old description",
                eventDateTime,
                new BigDecimal("100.00"),
                "Old address",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                50);

        UpdateEventRequest request = new UpdateEventRequest(
                "Event updated",
                "Updated description",
                eventDateTime,
                new BigDecimal("180.00"),
                "New address",
                new BigDecimal("-34.61"),
                new BigDecimal("-58.39"),
                120,
                null,
                null,
                null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER))
                .thenReturn(false);

        assertThrows(
                UnauthorizedEventAccessException.class,
                () -> eventService.updateEvent(eventId, request, organizer));
    }

    @Test
    void cancelEvent_WhenOrganizerAndModifiable_ShouldSetCancelledStatus() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Event to cancel",
                "Description",
                eventDateTime.plusDays(5),
                new BigDecimal("90.00"),
                "Venue",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                30);
        existingEvent.setStatus(EventStatus.PUBLISHED);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER))
                .thenReturn(true);

        eventService.cancelEvent(eventId, organizer);

        assertEquals(EventStatus.CANCELLED, existingEvent.getStatus());
        verify(eventRepository).save(existingEvent);
    }

    @Test
    void cancelEvent_WhenEventIsFinished_ShouldThrowEventNotModifiableException() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Finished event",
                "Description",
                eventDateTime,
                new BigDecimal("90.00"),
                "Venue",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                30);
        existingEvent.setStatus(EventStatus.FINISHED);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER))
                .thenReturn(true);

        assertThrows(
                EventNotModifiableException.class,
                () -> eventService.cancelEvent(eventId, organizer));
    }

    @Test
    void cancelEvent_WhenEventHasPurchasedTickets_ShouldThrowEventHasTicketsException() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Event with sales",
                "Description",
                eventDateTime,
                new BigDecimal("90.00"),
                "Venue",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                30);
        existingEvent.setStatus(EventStatus.PUBLISHED);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER))
                .thenReturn(true);
        when(ticketRepository.countByEventAndStatusIn(
                existingEvent, List.of(TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED)))
                .thenReturn(1L);

        assertThrows(
                EventHasTicketsException.class,
                () -> eventService.cancelEvent(eventId, organizer));
        verify(eventRepository, never()).save(existingEvent);
    }

    @Test
    void getPublishedEvents_ShouldReturnMappedResponses() {
        InPersonEvent inPersonEvent = new InPersonEvent(
                "In-person event",
                "Conference",
                eventDateTime,
                new BigDecimal("120.00"),
                "Calle 123",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                100);
        VirtualEvent virtualEvent = new VirtualEvent(
                "Webinar",
                "Online session",
                eventDateTime.plusHours(1),
                new BigDecimal("30.00"),
                "https://zoom.example.com",
                "Zoom",
                "Use the link");

        inPersonEvent.setId(UUID.randomUUID());
        virtualEvent.setId(UUID.randomUUID());

        when(eventRepository.findByStatusOrderByDateTimeAsc(eq(EventStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inPersonEvent, virtualEvent)));
        when(ticketRepository.countByEventInAndStatusIn(eq(List.of(inPersonEvent)), any()))
                .thenReturn(List.of(new EventTicketCount(inPersonEvent.getId(), 30)));

        List<EventResponse> response = eventService.getPublishedEvents(Pageable.ofSize(20)).getContent();

        assertEquals(2, response.size());
        assertEquals("IN_PERSON", response.get(0).eventType());
        assertEquals(70, response.get(0).availableTickets());
        assertEquals("VIRTUAL", response.get(1).eventType());
        assertNull(response.get(1).availableTickets());
        verify(ticketRepository, never()).countByEventAndStatusIn(any(), any());
    }

    @Test
    void getEvent_InPerson_ReturnsRemainingTicketsWithoutGoingNegative() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent event = new InPersonEvent(
                "Sold out", "Conference", eventDateTime, new BigDecimal("120.00"),
                "Calle 123", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 10);
        event.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        // La capacidad pudo bajar por debajo de lo vendido antes de esta validación.
        when(ticketRepository.countByEventAndStatusIn(eq(event), any())).thenReturn(12L);

        assertEquals(0, eventService.getEvent(eventId, null).availableTickets());
    }

    @Test
    void getEvent_WhenIdDoesNotExist_ShouldThrowEventNotFoundException() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> eventService.getEvent(eventId, null));
    }

    @Test
    void finishPastEvents_WhenPublishedEventIsPast_MarksItFinished() {
        InPersonEvent pastEvent = new InPersonEvent(
                "Past event",
                "Description",
                eventDateTime.minusHours(1),
                new BigDecimal("90.00"),
                "Venue",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                30);
        pastEvent.setStatus(EventStatus.PUBLISHED);

        when(eventRepository.findByStatusAndDateTimeBefore(
                eq(EventStatus.PUBLISHED), any(LocalDateTime.class)))
                .thenReturn(List.of(pastEvent));

        eventService.finishPastEvents();

        assertEquals(EventStatus.FINISHED, pastEvent.getStatus());
        verify(eventRepository).save(pastEvent);
    }

    @Test
    void publishEvent_WhenOrganizerAndDraft_ShouldSetPublishedStatusAndReturnResponse() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Event to publish",
                "Description",
                LocalDateTime.now().plusDays(5),
                new BigDecimal("90.00"),
                "Venue",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                30);
        // default status is DRAFT in Event constructor

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER))
                .thenReturn(true);
        when(mpAccountRepository.existsByUser(organizer)).thenReturn(true);

        EventResponse response = eventService.publishEvent(eventId, organizer);

        assertEquals("PUBLISHED", response.status());
        verify(eventRepository).save(existingEvent);
    }

    @Test
    void updateEvent_WhenCapacityIsBelowSoldTickets_ShouldThrowBadRequest() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Event", "Description", eventDateTime, new BigDecimal("90.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 30);
        existingEvent.setStatus(EventStatus.PUBLISHED);
        UpdateEventRequest request = new UpdateEventRequest(
                "Event", "Description", eventDateTime, new BigDecimal("90.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 2,
                null, null, null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER)).thenReturn(true);
        when(ticketRepository.countByEventAndStatusIn(
                existingEvent, List.of(TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED))).thenReturn(3L);

        assertThrows(BadRequestException.class,
                () -> eventService.updateEvent(eventId, request, organizer));
        verify(eventRepository, never()).save(existingEvent);
    }

    @Test
    void updateEvent_WhenEventHasSalesAndPriceChanges_ShouldThrowConflict() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Event", "Description", eventDateTime, new BigDecimal("90.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 30);
        existingEvent.setStatus(EventStatus.PUBLISHED);
        UpdateEventRequest request = new UpdateEventRequest(
                "Event", "Description", eventDateTime, new BigDecimal("100.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 30,
                null, null, null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER)).thenReturn(true);
        when(ticketRepository.countByEventAndStatusIn(
                existingEvent, List.of(TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED))).thenReturn(1L);

        assertThrows(EventHasSalesException.class,
                () -> eventService.updateEvent(eventId, request, organizer));
        verify(eventRepository, never()).save(existingEvent);
    }

    @Test
    void publishEvent_WhenDateHasPassed_ShouldThrowEventNotPublishableException() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent existingEvent = new InPersonEvent(
                "Past event", "Description", LocalDateTime.now().minusMinutes(1),
                new BigDecimal("90.00"), "Venue", new BigDecimal("-34.60"),
                new BigDecimal("-58.38"), 30);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                existingEvent, organizer, EventRole.ORGANIZER)).thenReturn(true);

        assertThrows(EventNotPublishableException.class,
                () -> eventService.publishEvent(eventId, organizer));
        verify(eventRepository, never()).save(existingEvent);
    }

    @Test
    void publishEvent_WhenEventIsFree_DoesNotRequireMpAccount() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent freeEvent = new InPersonEvent(
                "Free event", "Description", LocalDateTime.now().plusDays(5),
                BigDecimal.ZERO, "Venue", new BigDecimal("-34.60"),
                new BigDecimal("-58.38"), 30);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(freeEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                freeEvent, organizer, EventRole.ORGANIZER)).thenReturn(true);

        EventResponse response = eventService.publishEvent(eventId, organizer);

        assertEquals("PUBLISHED", response.status());
        verify(mpAccountRepository, never()).existsByUser(any());
    }

    @Test
    void publishEvent_WhenEventIsPaidAndMpNotLinked_ThrowsPreconditionRequired() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent paidEvent = new InPersonEvent(
                "Paid event", "Description", LocalDateTime.now().plusDays(5),
                new BigDecimal("90.00"), "Venue", new BigDecimal("-34.60"),
                new BigDecimal("-58.38"), 30);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(paidEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                paidEvent, organizer, EventRole.ORGANIZER)).thenReturn(true);
        when(mpAccountRepository.existsByUser(organizer)).thenReturn(false);

        assertThrows(MpAccountNotLinkedException.class,
                () -> eventService.publishEvent(eventId, organizer));
        verify(eventRepository, never()).save(paidEvent);
    }

    @Test
    void updateEvent_WhenPublishedFreeEventBecomesPaidWithoutMp_ThrowsPreconditionRequired() {
        UUID eventId = UUID.randomUUID();
        InPersonEvent freeEvent = new InPersonEvent(
                "Event", "Description", eventDateTime, BigDecimal.ZERO,
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 30);
        freeEvent.setStatus(EventStatus.PUBLISHED);
        UpdateEventRequest request = new UpdateEventRequest(
                "Event", "Description", eventDateTime, new BigDecimal("50.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 30,
                null, null, null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(freeEvent));
        when(eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                freeEvent, organizer, EventRole.ORGANIZER)).thenReturn(true);
        when(mpAccountRepository.existsByUser(organizer)).thenReturn(false);

        assertThrows(MpAccountNotLinkedException.class,
                () -> eventService.updateEvent(eventId, request, organizer));
        verify(eventRepository, never()).save(freeEvent);
    }
}
