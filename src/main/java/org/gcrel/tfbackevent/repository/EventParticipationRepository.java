package org.gcrel.tfbackevent.repository;

import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.EventParticipationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventParticipationRepository extends JpaRepository<EventParticipation, EventParticipationId> {
}
