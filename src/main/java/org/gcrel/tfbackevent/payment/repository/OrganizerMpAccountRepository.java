package org.gcrel.tfbackevent.payment.repository;

import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizerMpAccountRepository extends JpaRepository<OrganizerMpAccount, UUID> {
    Optional<OrganizerMpAccount> findByUser(User user);

    boolean existsByUser(User user);

    @Query("""
                SELECT a FROM OrganizerMpAccount a
                JOIN EventParticipation ep ON ep.user = a.user
                JOIN TicketOrder o ON o.event = ep.event
                JOIN Payment p ON p.order = o
                WHERE p.transactionId = :transactionId
                AND ep.id.eventRole = :role
            """)
    Optional<OrganizerMpAccount> findByPaymentTransactionId(
            @Param("transactionId") String transactionId,
            @Param("role") EventRole role);
}
