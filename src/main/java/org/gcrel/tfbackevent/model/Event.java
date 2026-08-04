package org.gcrel.tfbackevent.model;

import jakarta.persistence.*;
import org.gcrel.tfbackevent.model.enums.EventStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "events")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(name = "base_price", nullable = false)
    private Double basePrice;

    public Event() {}

    public Event(String title, String description, LocalDateTime dateTime, Double basePrice) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.dateTime = dateTime;
        this.status = EventStatus.DRAFT;
        this.basePrice = basePrice;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public UUID getId() {
        return id;
    }
}