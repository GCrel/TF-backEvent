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
import org.gcrel.tfbackevent.event.dto.request.UpdateSectorsRequest;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.dto.response.SectorResponse;
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
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final ImageStorageService imageStorageService;
    private final SectorService sectorService;

    public EventService(EventRepository eventRepository, EventParticipationRepository eventParticipationRepository,
            OrganizerMpAccountRepository mpAccountRepository, TicketRepository ticketRepository,
            EventWindowProperties eventWindowProperties, ImageStorageService imageStorageService,
            SectorService sectorService) {
        this.eventRepository = eventRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.mpAccountRepository = mpAccountRepository;
        this.ticketRepository = ticketRepository;
        this.eventWindowProperties = eventWindowProperties;
        this.imageStorageService = imageStorageService;
        this.sectorService = sectorService;
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

        requireMpAccountIfPaid(highestPrice(event), organizer);
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
        // Con sectores, el precio y el cupo se derivan de ellos: se ignoran los del
        // formulario.
        boolean derived = event instanceof InPersonEvent inPerson && sectorService.hasSectors(inPerson);
        BigDecimal basePrice = derived ? event.getBasePrice() : request.basePrice();
        Integer capacity = derived ? ((InPersonEvent) event).getCapacity() : request.capacity();
        validateSalesConstraints(event, basePrice, capacity, request.dateTime());
        if (event.getStatus() == EventStatus.PUBLISHED) {
            requireMpAccountIfPaid(derived ? highestPrice(event) : basePrice, organizer);
        }

        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setDateTime(request.dateTime());
        event.setBasePrice(basePrice);

        if (event instanceof InPersonEvent inPerson) {
            inPerson.setAddress(request.address());
            inPerson.setLatitude(request.latitude());
            inPerson.setLongitude(request.longitude());
            inPerson.setCapacity(capacity);
        } else if (event instanceof VirtualEvent virtual) {
            virtual.setAccessLink(request.accessLink());
            virtual.setPlatform(request.platform());
            virtual.setAccessInstructions(request.accessInstructions());
        }

        eventRepository.save(event);
        return toResponse(event, true);
    }

    @Transactional
    public EventResponse updateImage(UUID id, MultipartFile file, User organizer) {
        Event event = findModifiableEventForUpdate(id, organizer);
        String previousKey = event.getImageKey();
        String newKey = imageStorageService.store(file);
        deleteImageAfterCompletion(previousKey, newKey);

        event.setImageKey(newKey);
        eventRepository.save(event);
        return toResponse(event, true);
    }

    @Transactional
    public void deleteImage(UUID id, User organizer) {
        Event event = findModifiableEventForUpdate(id, organizer);
        String previousKey = event.getImageKey();
        if (previousKey == null) {
            return;
        }
        deleteImageAfterCompletion(previousKey, null);

        event.setImageKey(null);
        eventRepository.save(event);
    }

    @Transactional
    public EventResponse updateLayoutImage(UUID id, MultipartFile file, User organizer) {
        InPersonEvent event = requireInPerson(findModifiableEventForUpdate(id, organizer));
        String previousKey = event.getLayoutImageKey();
        String newKey = imageStorageService.store(file);
        deleteImageAfterCompletion(previousKey, newKey);

        event.setLayoutImageKey(newKey);
        eventRepository.save(event);
        return toResponse(event, true);
    }

    // El plano es opcional: quitarlo no afecta a los sectores.
    @Transactional
    public void deleteLayoutImage(UUID id, User organizer) {
        InPersonEvent event = requireInPerson(findModifiableEventForUpdate(id, organizer));
        String previousKey = event.getLayoutImageKey();
        if (previousKey == null) {
            return;
        }
        deleteImageAfterCompletion(previousKey, null);

        event.setLayoutImageKey(null);
        eventRepository.save(event);
    }

    @Transactional
    public List<SectorResponse> replaceSectors(UUID id, UpdateSectorsRequest request, User organizer) {
        InPersonEvent event = requireInPerson(findModifiableEventForUpdate(id, organizer));
        List<SectorResponse> sectors = sectorService.replaceSectors(event, request.sectors());
        if (event.getStatus() == EventStatus.PUBLISHED) {
            requireMpAccountIfPaid(highestPrice(event), organizer);
        }
        eventRepository.save(event);
        return sectors;
    }

    private InPersonEvent requireInPerson(Event event) {
        if (Hibernate.unproxy(event) instanceof InPersonEvent inPerson) {
            return inPerson;
        }
        throw new BadRequestException("El plano y los sectores solo están disponibles para eventos presenciales.");
    }

    // Un evento es pago si cobra algo en cualquiera de sus sectores, aunque el
    // precio
    // "desde" (basePrice) sea 0.
    private BigDecimal highestPrice(Event event) {
        BigDecimal sectorPrice = Hibernate.unproxy(event) instanceof InPersonEvent inPerson
                ? sectorService.maxPrice(inPerson)
                : null;
        return sectorPrice != null ? sectorPrice : event.getBasePrice();
    }

    // El bloqueo evita que dos subidas simultáneas lean la misma imagen anterior y
    // dejen una huérfana.
    private Event findModifiableEventForUpdate(UUID id, User organizer) {
        Event event = eventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        validateOwnership(event, organizer);
        validateModifiable(event);
        return event;
    }

    private void deleteImageAfterCompletion(String keyIfCommitted, String keyIfRolledBack) {
        DeferredImageDeletion.afterCompletion(imageStorageService, keyIfCommitted, keyIfRolledBack);
    }

    private void requireMpAccountIfPaid(BigDecimal price, User organizer) {
        if (price.signum() > 0 && !mpAccountRepository.existsByUser(organizer)) {
            throw new MpAccountNotLinkedException();
        }
    }

    private void validateSalesConstraints(
            Event event, BigDecimal basePrice, Integer capacity, LocalDateTime dateTime) {
        long soldTickets = ticketRepository.countByEventAndStatusIn(event, RESERVED_STATUSES);
        if (event instanceof InPersonEvent
                && capacity != null && capacity < soldTickets) {
            throw new BadRequestException("La capacidad no puede ser menor a las entradas vendidas.");
        }
        if (soldTickets > 0
                && (event.getBasePrice().compareTo(basePrice) != 0
                        || !event.getDateTime().equals(dateTime))) {
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
        return toResponses(eventRepository.search(
                EventStatus.PUBLISHED,
                eventClass,
                likePattern(query),
                from == null ? null : from.atStartOfDay(),
                // `to` es inclusivo: abarca todo ese día.
                to == null ? null : to.plusDays(1).atStartOfDay(),
                withoutSort(pageable)));
    }

    // Para el panel de administración: todos los estados, los más recientes primero.
    public Page<EventResponse> getAllEvents(
            EventStatus status, EventType type, String query, Pageable pageable) {
        Specification<Event> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (type != null) {
            Class<? extends Event> eventClass = type == EventType.IN_PERSON ? InPersonEvent.class : VirtualEvent.class;
            spec = spec.and((root, q, cb) -> cb.equal(root.type(), eventClass));
        }
        String pattern = likePattern(query);
        if (pattern != null) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("description")), pattern, '\\')));
        }
        return toResponses(eventRepository.findAll(spec, PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "dateTime", "id"))));
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

    // Se escapan los comodines de LIKE para que '%' y '_' se busquen literalmente.
    private static String likePattern(String query) {
        return query == null || query.isBlank()
                ? null
                : "%" + query.strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
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
        cancel(event);
    }

    // Misma regla que para el organizador (solo sin tickets), sin exigir serlo.
    @Transactional
    public Event cancelEventAsAdmin(UUID id) {
        Event event = eventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        cancel(event);
        return event;
    }

    private void cancel(Event event) {
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
        List<SectorResponse> sectors = event instanceof InPersonEvent inPerson
                ? sectorService.getSectors(inPerson)
                : null;
        return toResponse(event, reserved, isOrganizer, organizerName, sectors);
    }

    // Cuenta las entradas y busca los organizadores de toda la página con una
    // consulta para cada cosa.
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
                : 0, null, organizerByEvent.get(event.getId()), null));
    }

    private EventResponse toResponse(Event event, long reservedTickets, Boolean isOrganizer,
            String organizerName, List<SectorResponse> sectors) {
        if (event instanceof InPersonEvent inPerson) {
            return new EventResponse(inPerson, reservedTickets, isOrganizer, organizerName, sectors);
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
