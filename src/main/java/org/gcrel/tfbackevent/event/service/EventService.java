package org.gcrel.tfbackevent.event.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.config.EventWindowProperties;
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
import org.gcrel.tfbackevent.event.repository.dto.EventOrganizerName;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    // Estados que ocupan cupo: las pendientes de pago también reservan lugar.
    private static final List<TicketStatus> RESERVED_STATUSES = List.of(
            TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED);

    private final EventRepository eventRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final OrganizerMpAccountRepository mpAccountRepository;
    private final TicketRepository ticketRepository;
    private final EventWindowProperties eventWindowProperties;

    public EventService(EventRepository eventRepository, EventParticipationRepository eventParticipationRepository,
            OrganizerMpAccountRepository mpAccountRepository, TicketRepository ticketRepository,
            EventWindowProperties eventWindowProperties) {
        this.eventRepository = eventRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.mpAccountRepository = mpAccountRepository;
        this.ticketRepository = ticketRepository;
        this.eventWindowProperties = eventWindowProperties;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User organizer) {
        Event event = buildEvent(request);
        eventRepository.save(event);

        EventParticipation participation = new EventParticipation(organizer, event, EventRole.ORGANIZER);
        eventParticipationRepository.save(participation);

        return toResponse(event, true);
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

        requireMpAccountIfPaid(event.getBasePrice(), organizer);
        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);

        return toResponse(event, true);
    }

    @Transactional
    public EventResponse updateEvent(UUID id, UpdateEventRequest request, User organizer) {
        Event event = eventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        validateOwnership(event, organizer);
        validateModifiable(event);
        validateTypeSpecificFields(event, request);
        validateSalesConstraints(event, request);
        if (event.getStatus() == EventStatus.PUBLISHED) {
            requireMpAccountIfPaid(request.basePrice(), organizer);
        }

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
        return toResponse(event, true);
    }

    private void requireMpAccountIfPaid(BigDecimal price, User organizer) {
        if (price.signum() > 0 && !mpAccountRepository.existsByUser(organizer)) {
            throw new MpAccountNotLinkedException();
        }
    }

    private void validateSalesConstraints(Event event, UpdateEventRequest request) {
        long soldTickets = ticketRepository.countByEventAndStatusIn(event, RESERVED_STATUSES);
        if (event instanceof InPersonEvent
                && request.capacity() != null && request.capacity() < soldTickets) {
            throw new BadRequestException("La capacidad no puede ser menor a las entradas vendidas.");
        }
        if (soldTickets > 0
                && (event.getBasePrice().compareTo(request.basePrice()) != 0
                        || !event.getDateTime().equals(request.dateTime()))) {
            throw new EventHasSalesException();
        }
    }

    public EventResponse getEvent(UUID id, User viewer) {
        Event event = findEventById(id);
        boolean organizer = viewer != null && isOrganizer(event, viewer);
        if (event.getStatus() != EventStatus.PUBLISHED && !organizer) {
            throw new EventNotFoundException(id);
        }
        return toResponse(event, organizer);
    }

    public Page<EventResponse> getPublishedEvents(
            EventType type, String query, LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("La fecha 'from' no puede ser posterior a 'to'.");
        }
        Class<? extends Event> eventClass = type == null ? null : switch (type) {
            case IN_PERSON -> InPersonEvent.class;
            case VIRTUAL -> VirtualEvent.class;
        };
        // Se escapan los comodines de LIKE para que '%' y '_' se busquen literalmente.
        String pattern = query == null || query.isBlank()
                ? null
                : "%" + query.strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return toResponses(eventRepository.search(
                EventStatus.PUBLISHED,
                eventClass,
                pattern,
                from == null ? null : from.atStartOfDay(),
                // `to` es inclusivo: abarca todo ese día.
                to == null ? null : to.plusDays(1).atStartOfDay(),
                withoutSort(pageable)));
    }

    // Los más recientes primero; `status` null devuelve todos los estados.
    public Page<EventResponse> getMyEvents(User organizer, EventStatus status, Pageable pageable) {
        return getParticipantEvents(organizer, EventRole.ORGANIZER, status, Sort.Direction.DESC, pageable);
    }

    // Los próximos primero: es la agenda de trabajo del controlador.
    public Page<EventResponse> getControlledEvents(User controller, Pageable pageable) {
        return getParticipantEvents(
                controller, EventRole.CONTROLLER, EventStatus.PUBLISHED, Sort.Direction.ASC, pageable);
    }

    private Page<EventResponse> getParticipantEvents(
            User user, EventRole role, EventStatus status, Sort.Direction direction, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(direction, "event.dateTime"));
        Page<Event> events = status == null
                ? eventRepository.findByParticipant(user, role, page)
                : eventRepository.findByParticipantAndStatus(user, role, status, page);
        return toResponses(events);
    }

    private Pageable withoutSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    @Transactional
    @Scheduled(fixedDelayString = "${event.finish.retry-delay-ms:60000}")
    public void finishPastEvents() {
        eventRepository.findByStatusAndDateTimeBefore(
                EventStatus.PUBLISHED, LocalDateTime.now().minusHours(eventWindowProperties.afterHours()))
                .forEach(event -> {
                    event.setStatus(EventStatus.FINISHED);
                    eventRepository.save(event);
                });
    }

    @Transactional
    public void cancelEvent(UUID id, User organizer) {
        Event event = eventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        validateOwnership(event, organizer);
        validateModifiable(event);
        if (ticketRepository.countByEventAndStatusIn(event, RESERVED_STATUSES) > 0) {
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

    private EventResponse toResponse(Event event, Boolean isOrganizer) {
        long reserved = event instanceof InPersonEvent
                ? ticketRepository.countByEventAndStatusIn(event, RESERVED_STATUSES)
                : 0;
        String organizerName = eventParticipationRepository
                .findByEventAndId_EventRole(event, EventRole.ORGANIZER)
                .map(participation -> participation.getUser().getName())
                .orElse(null);
        return toResponse(event, reserved, isOrganizer, organizerName);
    }

    // Cuenta las entradas y busca los organizadores de toda la página con una consulta para cada cosa.
    private Page<EventResponse> toResponses(Page<Event> events) {
        List<Event> inPersonEvents = events.stream()
                .filter(InPersonEvent.class::isInstance)
                .toList();
        Map<UUID, Long> reservedByEvent = inPersonEvents.isEmpty()
                ? Map.of()
                : ticketRepository.countByEventInAndStatusIn(inPersonEvents, RESERVED_STATUSES)
                        .stream()
                        .collect(Collectors.toMap(EventTicketCount::eventId, EventTicketCount::count));
        Map<UUID, String> organizerByEvent = events.isEmpty()
                ? Map.of()
                : eventParticipationRepository.findNamesByEventInAndRole(events.getContent(), EventRole.ORGANIZER)
                        .stream()
                        .collect(Collectors.toMap(
                                EventOrganizerName::eventId, EventOrganizerName::name, (first, other) -> first));
        return events.map(event -> toResponse(event, event instanceof InPersonEvent
                ? reservedByEvent.getOrDefault(event.getId(), 0L)
                : 0, null, organizerByEvent.get(event.getId())));
    }

    private EventResponse toResponse(
            Event event, long reservedTickets, Boolean isOrganizer, String organizerName) {
        if (event instanceof InPersonEvent inPerson) {
            return new EventResponse(inPerson, reservedTickets, isOrganizer, organizerName);
        } else if (event instanceof VirtualEvent virtual) {
            return new EventResponse(virtual, isOrganizer, organizerName);
        }
        throw new InvalidEventTypeException();
    }

    private void validateOwnership(Event event, User organizer) {
        if (!isOrganizer(event, organizer)) {
            throw new UnauthorizedEventAccessException();
        }
    }

    private boolean isOrganizer(Event event, User organizer) {
        return eventParticipationRepository.existsByEventAndUserAndId_EventRole(
                event, organizer, EventRole.ORGANIZER);
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
