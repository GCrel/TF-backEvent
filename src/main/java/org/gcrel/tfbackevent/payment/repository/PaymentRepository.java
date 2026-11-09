package org.gcrel.tfbackevent.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByTransactionId(String transactionId);

    @EntityGraph(attributePaths = { "order", "order.event", "order.buyer" })
    Optional<Payment> findWithDetailsById(UUID id);

    @Override
    @EntityGraph(attributePaths = { "order", "order.event", "order.buyer" })
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    @EntityGraph(attributePaths = { "order", "order.event" })
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
}
