package org.gcrel.tfbackevent.payment.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByTicket(Ticket ticket);

    Optional<Payment> findByTransactionId(String transactionId);

    @EntityGraph(attributePaths = { "ticket", "ticket.attendee", "ticket.event" })
    List<Payment> findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
            Collection<DeliveryStatus> statuses, LocalDateTime now);

    List<Payment> findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
            DeliveryStatus status, LocalDateTime time);
}
