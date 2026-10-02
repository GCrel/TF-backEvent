package org.gcrel.tfbackevent.event.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.dto.request.CreateEventRequest;
import org.gcrel.tfbackevent.event.dto.request.UpdateEventRequest;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.exception.EventAlreadyPublishedException;
import org.gcrel.tfbackevent.event.exception.EventHasSalesException;
import org.gcrel.tfbackevent.event.exception.EventHasTicketsException;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.exception.EventNotModifiableException;
import org.gcrel.tfbackevent.event.exception.EventNotPublishableException;
import org.gcrel.tfbackevent.event.exception.InvalidEventTypeException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final OrganizerMpAccountRepository mpAccountRepository;
    private final TicketRepository ticketRepository;

    public EventService(EventRepository eventRepository, EventParticipationRepository eventParticipationRepository,
            OrganizerMpAccountRepository mpAccountRepository, TicketRepository ticketRepository) {
        this.eventRepository = eventRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.mpAccountRepository = mpAccountRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User organizer) {
        Event event = buildEvent(request);
        eventRepository.save(event);

        EventParticipation participation = new EventParticipation(organizer, event, EventRole.ORGANIZER);
        eventParticipationRepository.save(participation);

        return toResponse(event);
    }

    @Transactional
    public EventResponse publishEvent(UUID id, User organizer) {
        Event event = findEventById(id);
        validateOwnership(event, organizer);
        validateModifiable(event);

        if (event.getStatus() == EventStatus.PUBLISHED) {
            throw new EventAlreadyPublishedException();
        }
        if (!event.getDateTime().isAfter(LocalDateTime.now())) {
            throw new EventNotPublishableException();
        }

        if (!mpAccountRepository.existsByUser(organizer)) {
            throw new MpAccountNotLinkedException();
        }
        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);

        return toResponse(event);
    }

    @Transactional
    public EventResponse updateEvent(UUID id, UpdateEventRequest request, User organizer) {
        Event event = findEventById(id);
        validateOwnership(event, organizer);
        validateModifiable(event);
        validateTypeSpecificFields(event, request);
        validateSalesConstraints(event, request);

        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setDateTime(request.dateTime());
        event.setBasePrice(request.basePrice());

        if (event instanceof InPersonEvent inPerson) {
            inPerson.setAddress(request.address());
            inPerson.setLatitude(request.latitude());
            inPerson.setLongitude(request.longitude());
            inPerson.setCapacity(request.capacity());
        } else if (event instanceof VirtualEvent virtual) {
            virtual.setAccessLink(request.accessLink());
            virtual.setPlatform(request.platform());
            virtual.setAccessInstructions(request.accessInstructions());
        }

        eventRepository.save(event);
        return toResponse(event);
    }

    private void validateSalesConstraints(Event event, UpdateEventRequest request) {
        long soldTickets = ticketRepository.countByEventAndStatusIn(
                event, List.of(TicketStatus.ACTIVE, TicketStatus.USED));
        if (event instanceof InPersonEvent
                && request.capacity() != null && request.capacity() < soldTickets) {
            throw new BadRequestException("La capacidad no puede ser menor a las entradas vendidas.");
        }
        if (soldTickets > 0
                && (!event.getBasePrice().equals(request.basePrice())
                        || !event.getDateTime().equals(request.dateTime()))) {
            throw new EventHasSalesException();
        }
    }

    public EventResponse getEvent(UUID id) {
        return toResponse(findEventById(id));
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    @Scheduled(fixedDelayString = "${event.finish.retry-delay-ms:60000}")
    public void finishPastEvents() {
        eventRepository.findByStatusAndDateTimeBefore(EventStatus.PUBLISHED, LocalDateTime.now())
                .forEach(event -> {
                    event.setStatus(EventStatus.FINISHED);
                    eventRepository.save(event);
                });
    }

    @Transactional
    public void cancelEvent(UUID id, User organizer) {
        Event event = findEventById(id);
        validateOwnership(event, organizer);
        validateModifiable(event);
        if (ticketRepository.countByEventAndStatusIn(
                event, List.of(TicketStatus.ACTIVE, TicketStatus.USED)) > 0) {
            throw new EventHasTicketsException();
        }

        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
    }

    // private helper methods
    private Event buildEvent(CreateEventRequest request) {
        validateTypeSpecificFields(request);
        return switch (request.eventType()) {
            case IN_PERSON ->
                new InPersonEvent(
                        request.title(),
                        request.description(),
                        request.dateTime(),
                        request.basePrice(),
                        request.address(),
                        request.latitude(),
                        request.longitude(),
                        request.capacity());
            case VIRTUAL ->
                new VirtualEvent(
                        request.title(),
                        request.description(),
                        request.dateTime(),
                        request.basePrice(),
                        request.accessLink(),
                        request.platform(),
                        request.accessInstructions());
        };
    }

    private void validateTypeSpecificFields(CreateEventRequest request) {
        switch (request.eventType()) {
            case IN_PERSON -> validateInPersonFields(
                    request.address(), request.latitude(), request.longitude(), request.capacity());
            case VIRTUAL -> validateVirtualFields(request.accessLink(), request.platform());
            default -> throw new InvalidEventTypeException();
        }
    }

    private void validateTypeSpecificFields(Event event, UpdateEventRequest request) {
        if (event instanceof InPersonEvent) {
            validateInPersonFields(
                    request.address(), request.latitude(), request.longitude(), request.capacity());
        } else if (event instanceof VirtualEvent) {
            validateVirtualFields(request.accessLink(), request.platform());
        } else {
            throw new InvalidEventTypeException();
        }
    }

    private void validateInPersonFields(
            String address, BigDecimal latitude, BigDecimal longitude, Integer capacity) {
        if (address == null || address.isBlank()
                || latitude == null || longitude == null || capacity == null) {
            throw new BadRequestException(
                    "address, latitude, longitude and capacity are required for in-person events.");
        }
    }

    private void validateVirtualFields(String accessLink, String platform) {
        if (accessLink == null || accessLink.isBlank()
                || platform == null || platform.isBlank()) {
            throw new BadRequestException(
                    "accessLink and platform are required for virtual events.");
        }
    }

    private EventResponse toResponse(Event event) {
        if (event instanceof InPersonEvent inPerson) {
            return new EventResponse(inPerson);
        } else if (event instanceof VirtualEvent virtual) {
            return new EventResponse(virtual);
        }
        throw new InvalidEventTypeException();
    }

    private void validateOwnership(Event event, User organizer) {
        boolean isOwner = eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                event, organizer, EventRole.ORGANIZER);
        if (!isOwner) {
            throw new UnauthorizedEventAccessException();
        }
    }

    private void validateModifiable(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED
                || event.getStatus() == EventStatus.FINISHED) {
            throw new EventNotModifiableException();
        }
    }

    private Event findEventById(UUID id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }
}
