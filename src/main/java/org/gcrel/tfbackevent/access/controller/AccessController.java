package org.gcrel.tfbackevent.access.controller;

import java.util.UUID;

import org.gcrel.tfbackevent.access.dto.request.ValidateQrRequest;
import org.gcrel.tfbackevent.access.dto.response.AccessValidationResponse;
import org.gcrel.tfbackevent.access.dto.response.VirtualAccessResponse;
import org.gcrel.tfbackevent.access.service.AccessService;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {
    private final AccessService accessService;
    private final EventService eventService;

    public AccessController(AccessService accessService, EventService eventService) {
        this.accessService = accessService;
        this.eventService = eventService;
    }

    @PostMapping("/validate-qr")
    public ResponseEntity<AccessValidationResponse> validateQr(
            @Validated @RequestBody ValidateQrRequest request,
            @AuthenticationPrincipal User staff) {
        return ResponseEntity.ok(accessService.validateQr(request, staff));
    }

    @GetMapping("/virtual/{ticketId}")
    public ResponseEntity<VirtualAccessResponse> getVirtualAccess(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(accessService.getVirtualAccess(ticketId, user));
    }

    @GetMapping("/my-events")
    public ResponseEntity<Page<EventResponse>> getMyEvents(
            @AuthenticationPrincipal User controller,
            Pageable pageable) {
        return ResponseEntity.ok(eventService.getControlledEvents(controller, pageable));
    }
}
