package org.gcrel.tfbackevent.payment.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByTicket(Ticket ticket);

    Optional<Payment> findByTransactionId(String transactionId);

    @EntityGraph(attributePaths = { "ticket", "ticket.attendee", "ticket.event" })
    @Query("SELECT p FROM Payment p WHERE p.paymentStatus = :status "
            + "AND p.paymentDate < :cutoff "
            + "AND (p.nextReconcileAt IS NULL OR p.nextReconcileAt <= :now) "
            + "ORDER BY p.paymentDate ASC")
    List<Payment> findPendingForReconciliation(
            @Param("status") PaymentStatus status,
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Payment p SET p.nextReconcileAt = :at WHERE p.id = :id")
    void postponeReconciliation(@Param("id") UUID id, @Param("at") LocalDateTime at);

    @EntityGraph(attributePaths = { "ticket", "ticket.attendee", "ticket.event" })
    List<Payment> findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
            Collection<DeliveryStatus> statuses, LocalDateTime now);

    List<Payment> findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
            DeliveryStatus status, LocalDateTime time);
}
