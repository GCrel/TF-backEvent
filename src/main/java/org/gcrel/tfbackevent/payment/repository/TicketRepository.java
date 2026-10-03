package org.gcrel.tfbackevent.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByAttendee(User attendee);

    long countByEventAndStatusIn(Event event, Collection<TicketStatus> statuses);

    boolean existsByEventAndAttendeeAndStatusIn(Event event, User attendee, Collection<TicketStatus> statuses);

    @Query("""
            SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END
            FROM Ticket t
            JOIN EventParticipation ep ON ep.event = t.event
            WHERE ep.user = :organizer
            AND ep.id.eventRole = :role
            AND t.event.status = org.gcrel.tfbackevent.model.enums.EventStatus.PUBLISHED
            AND t.status IN :statuses
            """)
    boolean existsByOrganizerAndStatusIn(
            @Param("organizer") User organizer,
            @Param("role") EventRole role,
            @Param("statuses") Collection<TicketStatus> statuses);

    @EntityGraph(attributePaths = { "event", "attendee" })
    @Query("SELECT t FROM Ticket t WHERE t.status = :pending AND t.boughtAt < :before "
            + "AND NOT EXISTS (SELECT p.id FROM Payment p WHERE p.ticket = t)")
    List<Ticket> findPendingOrphans(
            @Param("pending") TicketStatus pending,
            @Param("before") java.time.LocalDateTime before);

    @Modifying
    @Transactional
    @Query("UPDATE Ticket t SET t.status = :cancelled "
            + "WHERE t.id = :id AND t.status = :pending "
            + "AND NOT EXISTS (SELECT p.id FROM Payment p WHERE p.ticket = t)")
    int cancelIfPendingWithoutPayment(
            @Param("id") UUID id,
            @Param("pending") TicketStatus pending,
            @Param("cancelled") TicketStatus cancelled);
}
