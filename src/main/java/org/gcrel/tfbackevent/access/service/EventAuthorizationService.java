package org.gcrel.tfbackevent.access.service;

import java.util.List;

import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.springframework.stereotype.Service;

@Service
public class EventAuthorizationService {
    private final EventParticipationRepository eventParticipationRepository;

    public EventAuthorizationService(EventParticipationRepository eventParticipationRepository) {
        this.eventParticipationRepository = eventParticipationRepository;
    }

    public boolean isOrganizer(Event event, User user) {
        return eventParticipationRepository.existsByEventAndUserAndId_EventRole(event, user, EventRole.ORGANIZER);
    }

    public boolean isStaff(Event event, User user) {
        return eventParticipationRepository.existsByEventAndUserAndId_EventRoleIn(event, user,
                List.of(EventRole.ORGANIZER, EventRole.CONTROLLER));
    }
}
