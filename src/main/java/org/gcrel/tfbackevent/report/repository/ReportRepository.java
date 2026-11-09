package org.gcrel.tfbackevent.report.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.report.repository.dto.ControllerValidations;
import org.gcrel.tfbackevent.report.repository.dto.EventRevenue;
import org.gcrel.tfbackevent.report.repository.dto.EventStatusCount;
import org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals;
import org.gcrel.tfbackevent.report.repository.dto.SaleRow;
import org.gcrel.tfbackevent.report.repository.dto.TicketStatusCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends Repository<Ticket, UUID> {
    String SOLD = "org.gcrel.tfbackevent.model.enums.TicketStatus.ACTIVE, "
            + "org.gcrel.tfbackevent.model.enums.TicketStatus.USED";
    String APPROVED = "org.gcrel.tfbackevent.model.enums.PaymentStatus.APPROVED";


    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.TicketStatusCount(t.status, COUNT(t)) "
            + "FROM Ticket t WHERE t.event.id = :eventId GROUP BY t.status")
    List<TicketStatusCount> ticketsByStatus(@Param("eventId") UUID eventId);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals("
            + "p.paymentStatus, COUNT(p), COALESCE(SUM(p.totalAmount), 0)) "
            + "FROM Payment p WHERE p.order.event.id = :eventId GROUP BY p.paymentStatus")
    List<PaymentStatusTotals> paymentsByStatus(@Param("eventId") UUID eventId);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.SaleRow(p.paymentDate, p.order.quantity, p.totalAmount) "
            + "FROM Payment p WHERE p.order.event.id = :eventId AND p.paymentStatus = " + APPROVED)
    List<SaleRow> approvedSales(@Param("eventId") UUID eventId);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.SaleRow(o.createdAt, o.quantity, o.totalAmount) "
            + "FROM TicketOrder o WHERE o.event.id = :eventId AND o.totalAmount = 0 "
            + "AND EXISTS (SELECT t.id FROM Ticket t WHERE t.order = o AND t.status IN (" + SOLD + "))")
    List<SaleRow> freeSales(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.event.id = :eventId AND t.status IN (" + SOLD + ") "
            + "AND t.accessedAt IS NOT NULL")
    long attendance(@Param("eventId") UUID eventId);

    @Query("SELECT t.accessedAt FROM Ticket t WHERE t.event.id = :eventId AND t.status IN (" + SOLD + ") "
            + "AND t.accessedAt IS NOT NULL")
    List<LocalDateTime> accessTimes(@Param("eventId") UUID eventId);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.ControllerValidations(v.id, v.name, COUNT(t)) "
            + "FROM Ticket t JOIN t.validatedBy v WHERE t.event.id = :eventId "
            + "AND t.status = org.gcrel.tfbackevent.model.enums.TicketStatus.USED GROUP BY v.id, v.name")
    List<ControllerValidations> validationsByController(@Param("eventId") UUID eventId);

    // Un conjunto de eventos (los ids no deben estar vacíos: lo comprueba el
    // servicio)

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.TicketStatusCount(t.status, COUNT(t)) "
            + "FROM Ticket t WHERE t.event.id IN :eventIds GROUP BY t.status")
    List<TicketStatusCount> ticketsByStatusIn(@Param("eventIds") Collection<UUID> eventIds);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals("
            + "p.paymentStatus, COUNT(p), COALESCE(SUM(p.totalAmount), 0)) "
            + "FROM Payment p WHERE p.order.event.id IN :eventIds GROUP BY p.paymentStatus")
    List<PaymentStatusTotals> paymentsByStatusIn(@Param("eventIds") Collection<UUID> eventIds);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.EventStatusCount(e.status, COUNT(e)) "
            + "FROM Event e WHERE e.id IN :eventIds GROUP BY e.status")
    List<EventStatusCount> eventsByStatusIn(@Param("eventIds") Collection<UUID> eventIds);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.EventRevenue("
            + "e.id, e.title, COALESCE(SUM(p.totalAmount), 0), COALESCE(SUM(o.quantity), 0)) "
            + "FROM Payment p JOIN p.order o JOIN o.event e "
            + "WHERE e.id IN :eventIds AND p.paymentStatus = " + APPROVED + " "
            + "GROUP BY e.id, e.title ORDER BY SUM(p.totalAmount) DESC, e.id")
    List<EventRevenue> topEventsByRevenueIn(@Param("eventIds") Collection<UUID> eventIds, Pageable limit);

    @Query("SELECT ep.event.id FROM EventParticipation ep "
            + "WHERE ep.user.id = :userId AND ep.id.eventRole = org.gcrel.tfbackevent.model.enums.EventRole.ORGANIZER")
    List<UUID> organizedEventIds(@Param("userId") UUID userId);

    // Global, por período: [from, to) con `to` exclusivo

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals("
            + "p.paymentStatus, COUNT(p), COALESCE(SUM(p.totalAmount), 0)) "
            + "FROM Payment p WHERE p.paymentDate >= :from AND p.paymentDate < :to GROUP BY p.paymentStatus")
    List<PaymentStatusTotals> paymentsByStatusBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM Payment p JOIN p.order o "
            + "WHERE p.paymentStatus = " + APPROVED + " AND p.paymentDate >= :from AND p.paymentDate < :to")
    long paidTicketsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM TicketOrder o "
            + "WHERE o.totalAmount = 0 AND o.createdAt >= :from AND o.createdAt < :to "
            + "AND EXISTS (SELECT t.id FROM Ticket t WHERE t.order = o AND t.status IN (" + SOLD + "))")
    long freeTicketsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.EventRevenue("
            + "e.id, e.title, COALESCE(SUM(p.totalAmount), 0), COALESCE(SUM(o.quantity), 0)) "
            + "FROM Payment p JOIN p.order o JOIN o.event e "
            + "WHERE p.paymentStatus = " + APPROVED + " AND p.paymentDate >= :from AND p.paymentDate < :to "
            + "GROUP BY e.id, e.title ORDER BY SUM(p.totalAmount) DESC, e.id")
    List<EventRevenue> topEventsByRevenueBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable limit);

    @Query("SELECT new org.gcrel.tfbackevent.report.repository.dto.EventStatusCount(e.status, COUNT(e)) "
            + "FROM Event e GROUP BY e.status")
    List<EventStatusCount> eventsByStatus();

    @Query("SELECT COUNT(u) FROM User u")
    long countUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt < :to")
    long countUsersCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
