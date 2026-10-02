package org.gcrel.tfbackevent.event.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    @Query("""
            SELECT ep.event FROM EventParticipation ep
            WHERE ep.user = :user
            AND ep.id.eventRole = :role
            AND ep.event.status = :status
            """)
    List<Event> findByOrganizerAndStatus(
            @Param("user") User user,
            @Param("role") EventRole role,
            @Param("status") EventStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findForTicketReservation(@Param("id") UUID id);
}
