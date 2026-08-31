package org.gcrel.tfbackevent.repository;

import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByAttendee(User attendee);
}