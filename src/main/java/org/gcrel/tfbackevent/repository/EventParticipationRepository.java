package org.gcrel.tfbackevent.repository;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.EventParticipationId;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventParticipationRepository extends JpaRepository<EventParticipation, EventParticipationId> {
    boolean existsByEventAndUserAndEventRole(Event event, User user, EventRole eventRole);
}
