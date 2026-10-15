package org.gcrel.tfbackevent.payment.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.repository.dto.EventTicketCount;
import org.gcrel.tfbackevent.payment.repository.dto.TicketAccessState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByAttendee(User attendee);

    long countByEventAndStatusIn(Event event, Collection<TicketStatus> statuses);

    // Conteo agrupado para armar listados de eventos con una sola consulta.
    @Query("SELECT new org.gcrel.tfbackevent.payment.repository.dto.EventTicketCount(t.event.id, COUNT(t)) "
            + "FROM Ticket t WHERE t.event IN :events AND t.status IN :statuses GROUP BY t.event.id")
    List<EventTicketCount> countByEventInAndStatusIn(
            @Param("events") Collection<? extends Event> events,
            @Param("statuses") Collection<TicketStatus> statuses);

    List<Ticket> findByOrder(TicketOrder order);

    List<Ticket> findByOrderAndStatus(TicketOrder order, TicketStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Ticket t SET t.status = :used, t.accessedAt = :now, t.validatedBy = :controller "
            + "WHERE t.id = :id AND t.status = :active")
    int markAsUsedIfActive(@Param("id") UUID id, @Param("now") LocalDateTime now,
            @Param("controller") User controller,
            @Param("active") TicketStatus active, @Param("used") TicketStatus used);

    @Modifying
    @Transactional
    @Query("UPDATE Ticket t SET t.accessedAt = :now WHERE t.id = :id AND t.accessedAt IS NULL")
    int markFirstAccess(@Param("id") UUID id, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = { "event", "attendee" })
    Optional<Ticket> findWithEventAndAttendeeById(UUID id);

    // Consulta de solo columnas: evita la caché de la sesión tras el UPDATE.
    @Query("SELECT new org.gcrel.tfbackevent.payment.repository.dto.TicketAccessState(t.status, t.accessedAt) "
            + "FROM Ticket t WHERE t.id = :id")
    Optional<TicketAccessState> findAccessStateById(@Param("id") UUID id);

    @Query("""
            SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END
            FROM Ticket t
            JOIN EventParticipation ep ON ep.event = t.event
            WHERE ep.user = :organizer
            AND ep.id.eventRole = :role
            AND t.event.status = org.gcrel.tfbackevent.model.enums.EventStatus.PUBLISHED
            AND t.event.basePrice > 0
            AND t.status IN :statuses
            """)
    boolean existsInPaidEventsByOrganizerAndStatusIn(
            @Param("organizer") User organizer,
            @Param("role") EventRole role,
            @Param("statuses") Collection<TicketStatus> statuses);

    @Modifying
    @Transactional
    @Query("UPDATE Ticket t SET t.status = :cancelled "
            + "WHERE t.order = :order AND t.status = :pending "
            + "AND NOT EXISTS (SELECT p.id FROM Payment p WHERE p.order = t.order)")
    int cancelPendingWithoutPayment(
            @Param("order") TicketOrder order,
            @Param("pending") TicketStatus pending,
            @Param("cancelled") TicketStatus cancelled);
}
