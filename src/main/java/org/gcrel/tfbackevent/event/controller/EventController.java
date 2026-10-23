package org.gcrel.tfbackevent.event.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.event.dto.request.CreateEventRequest;
import org.gcrel.tfbackevent.event.dto.request.UpdateEventRequest;
import org.gcrel.tfbackevent.event.dto.request.UpdateSectorsRequest;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.dto.response.SectorResponse;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Validated @RequestBody CreateEventRequest request, Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        EventResponse response = eventService.createEvent(request, organizer);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<EventResponse> publishEvent(
            @PathVariable UUID id, Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        EventResponse response = eventService.publishEvent(id, organizer);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable UUID id,
            @Validated @RequestBody UpdateEventRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        EventResponse response = eventService.updateEvent(id, request, organizer);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(
            @PathVariable UUID id, @AuthenticationPrincipal User viewer) {
        EventResponse response = eventService.getEvent(id, viewer);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<EventResponse>> getPublishedEvents(
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        Page<EventResponse> response = eventService.getPublishedEvents(eventType, q, from, to, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/mine")
    public ResponseEntity<Page<EventResponse>> getMyEvents(
            @AuthenticationPrincipal User organizer,
            @RequestParam(required = false) EventStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(eventService.getMyEvents(organizer, status, pageable));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> updateImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        return ResponseEntity.ok(eventService.updateImage(id, file, organizer));
    }

    @DeleteMapping("/{id}/image")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        eventService.deleteImage(id, organizer);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}/layout-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> updateLayoutImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        return ResponseEntity.ok(eventService.updateLayoutImage(id, file, organizer));
    }

    @DeleteMapping("/{id}/layout-image")
    public ResponseEntity<Void> deleteLayoutImage(@PathVariable UUID id, Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        eventService.deleteLayoutImage(id, organizer);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/sectors")
    public ResponseEntity<List<SectorResponse>> replaceSectors(
            @PathVariable UUID id,
            @Validated @RequestBody UpdateSectorsRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        return ResponseEntity.ok(eventService.replaceSectors(id, request, organizer));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelEvent(@PathVariable UUID id, Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        eventService.cancelEvent(id, organizer);
        return ResponseEntity.noContent().build();
    }
}
