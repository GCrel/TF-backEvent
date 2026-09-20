package org.gcrel.tfbackevent.repository;

import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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
