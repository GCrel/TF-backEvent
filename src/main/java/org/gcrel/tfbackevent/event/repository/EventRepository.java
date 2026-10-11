package org.gcrel.tfbackevent.event.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByStatusOrderByDateTimeAsc(EventStatus status, Pageable pageable);

    @Query("""
            SELECT ep.event FROM EventParticipation ep
            WHERE ep.user = :user
            AND ep.id.eventRole = :role
            ORDER BY ep.event.dateTime DESC
            """)
    List<Event> findByParticipant(
            @Param("user") User user,
            @Param("role") EventRole role,
            Pageable pageable);

    @Query("""
            SELECT ep.event FROM EventParticipation ep
            WHERE ep.user = :user
            AND ep.id.eventRole = :role
            AND ep.event.status = :status
            ORDER BY ep.event.dateTime ASC
            """)
    List<Event> findByParticipantAndStatus(
            @Param("user") User user,
            @Param("role") EventRole role,
            @Param("status") EventStatus status,
            Pageable pageable);

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

    List<Event> findByStatusAndDateTimeBefore(EventStatus status, java.time.LocalDateTime dateTime);

    @Query("""
            SELECT ep.event FROM EventParticipation ep
            WHERE ep.user = :user
            AND ep.id.eventRole = :role
            AND ep.event.status = :status
            AND ep.event.basePrice > 0
            AND NOT EXISTS (
                SELECT t.id FROM Ticket t
                WHERE t.event = ep.event
                AND t.status IN :openStatuses
            )
            """)
    List<Event> findPaidPublishedWithoutOpenTickets(
            @Param("user") User user,
            @Param("role") EventRole role,
            @Param("status") EventStatus status,
            @Param("openStatuses") java.util.Collection<TicketStatus> openStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);
}
