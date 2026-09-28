package org.gcrel.tfbackevent.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import org.gcrel.tfbackevent.model.enums.EventRole;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class EventParticipationId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_role")
    private EventRole eventRole;

    protected EventParticipationId() {
    }

    public EventParticipationId(UUID userId, UUID eventId, EventRole eventRole) {
        this.userId = userId;
        this.eventId = eventId;
        this.eventRole = eventRole;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        EventParticipationId that = (EventParticipationId) o;
        return Objects.equals(userId, that.userId)
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(eventRole, that.eventRole);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, eventId, eventRole);
    }
}
