package org.gcrel.tfbackevent.event.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.gcrel.tfbackevent.event.repository.dto.EventOrganizerName;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.EventParticipationId;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface EventParticipationRepository extends JpaRepository<EventParticipation, EventParticipationId> {
    boolean existsByEventAndUserAndId_EventRole(Event event, User user, EventRole eventRole);

    boolean existsByEventAndUserAndId_EventRoleIn(
            Event event, User user, Collection<EventRole> roles);

    @EntityGraph(attributePaths = "user")
    Optional<EventParticipation> findByEventAndId_EventRole(Event event, EventRole eventRole);

    @EntityGraph(attributePaths = "user")
    List<EventParticipation> findAllByEventAndId_EventRole(Event event, EventRole role);

    @Query("SELECT new org.gcrel.tfbackevent.event.repository.dto.EventOrganizerName(ep.event.id, ep.user.name) "
            + "FROM EventParticipation ep WHERE ep.event IN :events AND ep.id.eventRole = :role")
    List<EventOrganizerName> findNamesByEventInAndRole(
            @Param("events") Collection<? extends Event> events,
            @Param("role") EventRole role);

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM EventParticipation ep
            WHERE ep.event = :event
            AND ep.user = :user
            AND ep.id.eventRole = :role
            """)
    int removeParticipation(
            @Param("event") Event event,
            @Param("user") User user,
            @Param("role") EventRole role);
}
