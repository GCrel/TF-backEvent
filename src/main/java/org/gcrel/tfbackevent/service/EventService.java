package org.gcrel.tfbackevent.service;

import org.gcrel.tfbackevent.dto.request.CreateEventRequest;
import org.gcrel.tfbackevent.dto.request.UpdateEventRequest;
import org.gcrel.tfbackevent.dto.response.EventResponse;
import org.gcrel.tfbackevent.exception.EventNotFoundException;
import org.gcrel.tfbackevent.exception.EventNotModifiableException;
import org.gcrel.tfbackevent.exception.InvalidEventTypeException;
import org.gcrel.tfbackevent.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.model.*;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final EventParticipationRepository eventParticipationRepository;

    public EventService(EventRepository eventRepository, EventParticipationRepository eventParticipationRepository) {
        this.eventRepository = eventRepository;
        this.eventParticipationRepository = eventParticipationRepository;
    }

    public EventResponse createEvent(CreateEventRequest request, User organizer) {
        Event event = buildEvent(request);
        eventRepository.save(event);

        EventParticipation participation = new EventParticipation(organizer, event, EventRole.ORGANIZER);
        eventParticipationRepository.save(participation);

        return toResponse(event);
    }

    public EventResponse updateEvent(UUID id, UpdateEventRequest request, User organizer) {
        Event event = findEventById(id);
        validateOwnership(event, organizer);
        validateModifiable(event);

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setDateTime(request.getDateTime());
        event.setBasePrice(request.getBasePrice());

        if (event instanceof InPersonEvent inPerson) {
            inPerson.setAddress(request.getAddress());
            inPerson.setLatitude(request.getLatitude());
            inPerson.setLongitude(request.getLongitude());
            inPerson.setCapacity(request.getCapacity());
        } else if (event instanceof VirtualEvent virtual) {
            virtual.setAccessLink(request.getAccessLink());
            virtual.setPlatform(request.getPlatform());
            virtual.setAccessInstructions(request.getAccessInstructions());
        }

        eventRepository.save(event);
        return toResponse(event);
    }

    public EventResponse getEvent(UUID id) {
        return toResponse(findEventById(id));
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void cancelEvent(UUID id, User organizer) {
        Event event = findEventById(id);
        validateOwnership(event, organizer);
        validateModifiable(event);

        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
    }

    // private helper methods
    private Event buildEvent(CreateEventRequest request) {
        return switch (request.getEventType()) {
            case "IN_PERSON" -> new InPersonEvent(
                    request.getTitle(),
                    request.getDescription(),
                    request.getDateTime(),
                    request.getBasePrice(),
                    request.getAddress(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getCapacity()
            );
            case "VIRTUAL" -> new VirtualEvent(
                    request.getTitle(),
                    request.getDescription(),
                    request.getDateTime(),
                    request.getBasePrice(),
                    request.getAccessLink(),
                    request.getPlatform(),
                    request.getAccessInstructions()
            );
            default -> throw new InvalidEventTypeException();
        };
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
        boolean isOwner = eventParticipationRepository
                .existsByEventAndUserAndId_EventRole(event, organizer, EventRole.ORGANIZER);
        if (!isOwner) {
            throw new UnauthorizedEventAccessException();
        }
    }

    private void validateModifiable(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED ||
                event.getStatus() == EventStatus.FINISHED) {
            throw new EventNotModifiableException();
        }
    }

    private Event findEventById(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }
}
