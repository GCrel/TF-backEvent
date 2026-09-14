package org.gcrel.tfbackevent.model;

import jakarta.persistence.*;

import org.gcrel.tfbackevent.model.enums.EventRole;

import java.util.Objects;

@Entity
@Table(name = "event_participations")
public class EventParticipation {

    @EmbeddedId
    private EventParticipationId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Event event;

    protected EventParticipation() {
    }

    public EventParticipation(User user, Event event, EventRole eventRole) {
        this.id = new EventParticipationId(user.getId(), event.getId(), eventRole);
        this.user = user;
        this.event = event;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        EventParticipation that = (EventParticipation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
