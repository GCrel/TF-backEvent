package org.gcrel.tfbackevent.event.repository;

import java.util.UUID;

import org.gcrel.tfbackevent.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
}
