package org.gcrel.tfbackevent.access.controller;

import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.access.dto.request.AssignControllerRequest;
import org.gcrel.tfbackevent.access.dto.response.ControllerResponse;
import org.gcrel.tfbackevent.access.service.EventStaffService;
import org.gcrel.tfbackevent.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/controllers")
public class EventStaffController {
    private final EventStaffService eventStaffService;

    public EventStaffController(EventStaffService eventStaffService) {
        this.eventStaffService = eventStaffService;
    }

    @PostMapping
    public ResponseEntity<ControllerResponse> assign(
            @PathVariable UUID eventId,
            @Validated @RequestBody AssignControllerRequest request,
            Authentication authentication) {
        ControllerResponse response = eventStaffService.assign(
                eventId, request, (User) authentication.getPrincipal());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ControllerResponse>> list(
            @PathVariable UUID eventId, Authentication authentication) {
        return ResponseEntity.ok(eventStaffService.list(
                eventId, (User) authentication.getPrincipal()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID eventId,
            @PathVariable UUID userId,
            Authentication authentication) {
        eventStaffService.remove(eventId, userId, (User) authentication.getPrincipal());
        return ResponseEntity.noContent().build();
    }
}
