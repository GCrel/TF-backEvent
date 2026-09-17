package org.gcrel.tfbackevent.repository;

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
                JOIN a.user u
                JOIN EventParticipation ep ON ep.user = u
                JOIN ep.event e
                JOIN Ticket t ON t.event = e
                JOIN Payment p ON p.ticket = t
                WHERE p.transactionId = :transactionId
                AND ep.id.eventRole = :role
            """)
    Optional<OrganizerMpAccount> findByPaymentTransactionId(
            @Param("transactionId") String transactionId,
            @Param("role") EventRole role);
}
