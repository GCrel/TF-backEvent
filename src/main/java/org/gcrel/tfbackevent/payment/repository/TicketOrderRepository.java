package org.gcrel.tfbackevent.payment.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketOrderRepository extends JpaRepository<TicketOrder, UUID> {
    @EntityGraph(attributePaths = { "buyer", "event" })
    List<TicketOrder> findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
            Collection<DeliveryStatus> statuses, LocalDateTime now);

    List<TicketOrder> findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
            DeliveryStatus status, LocalDateTime time);

    // Órdenes con entradas aún pendientes y sin pago registrado (resultado del
    // cobro desconocido o proceso interrumpido).
    @EntityGraph(attributePaths = { "buyer", "event" })
    @Query("SELECT o FROM TicketOrder o WHERE o.createdAt < :before "
            + "AND EXISTS (SELECT t.id FROM Ticket t WHERE t.order = o AND t.status = :pending) "
            + "AND NOT EXISTS (SELECT p.id FROM Payment p WHERE p.order = o)")
    List<TicketOrder> findPendingOrphans(
            @Param("pending") TicketStatus pending,
            @Param("before") LocalDateTime before);
}
