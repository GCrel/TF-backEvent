package org.gcrel.tfbackevent.access.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.access.dto.request.ValidateQrRequest;
import org.gcrel.tfbackevent.access.dto.response.AccessValidationResponse;
import org.gcrel.tfbackevent.access.dto.response.VirtualAccessResponse;
import org.gcrel.tfbackevent.access.exception.AccessWindowClosedException;
import org.gcrel.tfbackevent.access.exception.TicketAlreadyUsedException;
import org.gcrel.tfbackevent.access.exception.TicketEventMismatchException;
import org.gcrel.tfbackevent.access.exception.TicketNotValidException;
import org.gcrel.tfbackevent.event.config.EventWindowProperties;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.exception.EventNotAvailableException;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.exception.InvalidEventTypeException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.repository.dto.TicketAccessState;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service
public class AccessService {
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final EventAuthorizationService authorization;
    private final EventWindowProperties windowProperties;
    private final EventService eventService;

    public AccessService(
            EventRepository eventRepository,
            TicketRepository ticketRepository,
            EventAuthorizationService authorization,
            EventWindowProperties windowProperties,
            EventService eventService) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.authorization = authorization;
        this.windowProperties = windowProperties;
        this.eventService = eventService;
    }

    public java.util.List<EventResponse> getMyEvents(User controller, org.springframework.data.domain.Pageable pageable) {
        return eventService.getMyEvents(controller, EventRole.CONTROLLER, EventStatus.PUBLISHED, pageable);
    }

    public AccessValidationResponse validateQr(ValidateQrRequest request, User staff) {
        UUID ticketId = parseTicketId(request.qrCode());
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new EventNotFoundException(request.eventId()));

        if (!authorization.isStaff(event, staff)) {
            throw new UnauthorizedEventAccessException();
        }

        if (event.getType() != EventType.IN_PERSON) {
            throw new InvalidEventTypeException();
        }
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotAvailableException();
        }
        validateWindow(event);

        Ticket ticket = ticketRepository.findWithEventAndAttendeeById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (!ticket.getEvent().getId().equals(event.getId())) {
            throw new TicketEventMismatchException();
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = ticketRepository.markAsUsedIfActive(
                ticketId, now, staff, TicketStatus.ACTIVE, TicketStatus.USED);
        if (updated == 0) {
            throw invalidTicket(ticketId);
        }
        return new AccessValidationResponse(
                ticketId, ticket.getAttendee().getName(), event.getTitle(), now);
    }

    public VirtualAccessResponse getVirtualAccess(UUID ticketId, User user) {
        Ticket ticket = ticketRepository.findWithEventAndAttendeeById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (!ticket.getAttendee().getId().equals(user.getId())) {
            throw new org.gcrel.tfbackevent.payment.exception.UnauthorizedTicketAccessException();
        }
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new org.gcrel.tfbackevent.payment.exception.TicketNotActiveException();
        }
        if (!(Hibernate.unproxy(ticket.getEvent()) instanceof org.gcrel.tfbackevent.model.VirtualEvent event)) {
            throw new InvalidEventTypeException();
        }
        ticketRepository.markFirstAccess(ticketId, LocalDateTime.now());
        return new VirtualAccessResponse(
                event.getTitle(),
                event.getDateTime(),
                event.getPlatform(),
                event.getAccessLink(),
                event.getAccessInstructions());
    }

    private UUID parseTicketId(String qrCode) {
        try {
            return UUID.fromString(qrCode.trim());
        } catch (IllegalArgumentException ex) {
            throw new org.gcrel.tfbackevent.common.exception.BadRequestException(
                    "El código QR no contiene un identificador de ticket válido.");
        }
    }

    private void validateWindow(Event event) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getDateTime().minusHours(windowProperties.beforeHours()))
                || !now.isBefore(event.getDateTime().plusHours(windowProperties.afterHours()))) {
            throw new AccessWindowClosedException();
        }
    }

    private RuntimeException invalidTicket(UUID ticketId) {
        TicketAccessState state = ticketRepository.findAccessStateById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        return state.status() == TicketStatus.USED
                ? new TicketAlreadyUsedException(state.accessedAt())
                : new TicketNotValidException();
    }
}
