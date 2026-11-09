package org.gcrel.tfbackevent.access.service;

import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.access.dto.request.AssignControllerRequest;
import org.gcrel.tfbackevent.access.dto.response.ControllerResponse;
import org.gcrel.tfbackevent.access.exception.ControllerAlreadyAssignedException;
import org.gcrel.tfbackevent.access.exception.ControllerNotAssignedException;
import org.gcrel.tfbackevent.access.exception.ControllerUserNotFoundException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.EmailNormalizer;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.exception.EventNotModifiableException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventStaffService {
    private final EventRepository eventRepository;
    private final EventParticipationRepository participationRepository;
    private final UserRepository userRepository;

    public EventStaffService(
            EventRepository eventRepository,
            EventParticipationRepository participationRepository,
            UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ControllerResponse assign(UUID eventId, AssignControllerRequest request, User organizer) {
        Event event = findEvent(eventId);
        requireOrganizer(event, organizer);
        if (event.getStatus() == EventStatus.CANCELLED
                || event.getStatus() == EventStatus.FINISHED) {
            throw new EventNotModifiableException();
        }
        if (event.getType() == EventType.VIRTUAL) {
            throw new BadRequestException("Los eventos virtuales no admiten controladores.");
        }

        User user = userRepository.findByEmail(EmailNormalizer.normalize(request.email()))
                .orElseThrow(() -> new ControllerUserNotFoundException(
                        "No hay ningún usuario con ese email."));
        if (!user.isEnabled()) {
            throw new ControllerUserNotFoundException("El usuario todavía no verificó su cuenta.");
        }
        if (user.getId().equals(organizer.getId())) {
            throw new BadRequestException("El organizador ya puede validar entradas.");
        }
        if (participationRepository.existsByEventAndUserAndId_EventRole(
                event, user, EventRole.CONTROLLER)) {
            throw new ControllerAlreadyAssignedException();
        }

        try {
            participationRepository.saveAndFlush(
                    new EventParticipation(user, event, EventRole.CONTROLLER));
        } catch (DataIntegrityViolationException exception) {
            throw new ControllerAlreadyAssignedException();
        }
        return new ControllerResponse(user);
    }

    @Transactional
    public void remove(UUID eventId, UUID userId, User organizer) {
        Event event = findEvent(eventId);
        requireOrganizer(event, organizer);
        User user = userRepository.getReferenceById(userId);
        if (participationRepository.removeParticipation(event, user, EventRole.CONTROLLER) == 0) {
            throw new ControllerNotAssignedException();
        }
    }

    @Transactional(readOnly = true)
    public List<ControllerResponse> list(UUID eventId, User organizer) {
        Event event = findEvent(eventId);
        requireOrganizer(event, organizer);
        return participationRepository.findAllByEventAndId_EventRole(event, EventRole.CONTROLLER)
                .stream()
                .map(participation -> new ControllerResponse(participation.getUser()))
                .toList();
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    private void requireOrganizer(Event event, User user) {
        if (!participationRepository.existsByEventAndUserAndId_EventRole(
                event, user, EventRole.ORGANIZER)) {
            throw new UnauthorizedEventAccessException();
        }
    }
}
