package org.gcrel.tfbackevent.controller;

import org.gcrel.tfbackevent.dto.request.CreateEventRequest;
import org.gcrel.tfbackevent.dto.request.UpdateEventRequest;
import org.gcrel.tfbackevent.dto.response.EventResponse;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@RequestBody CreateEventRequest request, Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        EventResponse response = eventService.createEvent(request, organizer);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable UUID id, @RequestBody UpdateEventRequest request, Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        EventResponse response = eventService.updateEvent(id, request, organizer);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable UUID id) {
        EventResponse response = eventService.getEvent(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents() {
        List<EventResponse> response = eventService.getAllEvents();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelEvent(@PathVariable UUID id, Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        eventService.cancelEvent(id, organizer);
        return ResponseEntity.noContent().build();
    }
}
