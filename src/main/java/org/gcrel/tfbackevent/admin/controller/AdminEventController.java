package org.gcrel.tfbackevent.admin.controller;

import java.util.UUID;

import org.gcrel.tfbackevent.admin.service.AdminEventService;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {
    private final AdminEventService adminEventService;

    public AdminEventController(AdminEventService adminEventService) {
        this.adminEventService = adminEventService;
    }

    @GetMapping
    public ResponseEntity<Page<EventResponse>> search(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(adminEventService.search(status, eventType, q, pageable));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal User actor) {
        adminEventService.cancel(actor, id);
        return ResponseEntity.noContent().build();
    }
}
