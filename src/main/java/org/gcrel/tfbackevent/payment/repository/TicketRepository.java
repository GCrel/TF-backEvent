package org.gcrel.tfbackevent.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByAttendee(User attendee);

    long countByEventAndStatusIn(Event event, Collection<TicketStatus> statuses);

    boolean existsByEventAndAttendeeAndStatusIn(Event event, User attendee, Collection<TicketStatus> statuses);
}
