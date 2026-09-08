package org.gcrel.tfbackevent.model;

import jakarta.persistence.*;

import org.gcrel.tfbackevent.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bought_at", nullable = false)
    private LocalDateTime boughtAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id", nullable = false)
    private User attendee;

    protected Ticket() {}

    public Ticket(LocalDateTime boughtAt, Event event, User attendee) {
        this.boughtAt = boughtAt;
        this.status = TicketStatus.ACTIVE;
        this.event = event;
        this.attendee = attendee;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return Objects.equals(id, ticket.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public UUID getId() {
        return id;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public User getAttendee() {
        return attendee;
    }

    public Event getEvent() {
        return event;
    }

    public LocalDateTime getBoughtAt() {
        return boughtAt;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }
}
