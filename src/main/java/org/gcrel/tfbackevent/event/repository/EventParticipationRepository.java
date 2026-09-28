package org.gcrel.tfbackevent.event.repository;

import java.util.Optional;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.EventParticipationId;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventParticipationRepository extends JpaRepository<EventParticipation, EventParticipationId> {
    boolean existsByEventAndUserAndId_EventRole(Event event, User user, EventRole eventRole);

    Optional<EventParticipation> findByEventAndEventRole(Event event, EventRole eventRole);
}
