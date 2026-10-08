package org.gcrel.tfbackevent.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.gcrel.tfbackevent.model.enums.TicketStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

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

    @Column(name = "accessed_at")
    private LocalDateTime accessedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private User validatedBy;

    protected Ticket() {
    }

    public Ticket(LocalDateTime boughtAt, Event event, User attendee) {
        this.boughtAt = boughtAt;
        this.status = TicketStatus.PENDING;
        this.event = event;
        this.attendee = attendee;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
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

    public User getValidatedBy() {
        return validatedBy;
    }

    public LocalDateTime getAccessedAt() {
        return accessedAt;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public void setAttendee(User attendee) {
        this.attendee = attendee;
    }

    public void setBoughtAt(LocalDateTime boughtAt) {
        this.boughtAt = boughtAt;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public void setAccessedAt(LocalDateTime accessedAt) {
        this.accessedAt = accessedAt;
    }

    public void setValidatedBy(User validatedBy) {
        this.validatedBy = validatedBy;
    }
}
