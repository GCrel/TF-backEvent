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

    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

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

    public Ticket(String qrCode, LocalDateTime boughtAt, Event event, User attendee) {
        this.qrCode = qrCode;
        this.boughtAt = boughtAt;
        this.status = TicketStatus.ACTIVE;
        this.event = event;
        this.attendee = attendee;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return Objects.equals(id, ticket.id) && Objects.equals(qrCode, ticket.qrCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, qrCode);
    }
}