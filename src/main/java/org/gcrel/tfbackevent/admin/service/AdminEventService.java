package org.gcrel.tfbackevent.admin.service;

import java.util.UUID;

import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminEventService {
    private final EventService eventService;
    private final EventParticipationRepository participationRepository;
    private final AuditService auditService;
    private final EmailService emailService;

    public AdminEventService(
            EventService eventService,
            EventParticipationRepository participationRepository,
            AuditService auditService,
            EmailService emailService) {
        this.eventService = eventService;
        this.participationRepository = participationRepository;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    public Page<EventResponse> search(EventStatus status, EventType type, String query, Pageable pageable) {
        return eventService.getAllEvents(status, type, query, pageable);
    }

    // Una sola transacción: la cancelación y su registro se confirman juntas.
    @Transactional
    public void cancel(User actor, UUID id) {
        Event event = eventService.cancelEventAsAdmin(id);
        auditService.record(actor, AuditAction.EVENT_CANCELLED, AuditTargetType.EVENT, id, event.getTitle());
        participationRepository.findByEventAndId_EventRole(event, EventRole.ORGANIZER)
                .ifPresent(organizer -> emailService.sendNotice(organizer.getUser().getEmail(),
                        "Se canceló tu evento",
                        "Un administrador canceló el evento \"" + event.getTitle() + "\"."));
    }
}
