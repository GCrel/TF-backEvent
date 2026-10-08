package org.gcrel.tfbackevent.access.controller;

import org.gcrel.tfbackevent.access.dto.request.ValidateQrRequest;
import org.gcrel.tfbackevent.access.dto.response.AccessValidationResponse;
import org.gcrel.tfbackevent.access.dto.response.VirtualAccessResponse;
import org.gcrel.tfbackevent.access.service.AccessService;
import org.gcrel.tfbackevent.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {
    private final AccessService accessService;

    public AccessController(AccessService accessService) {
        this.accessService = accessService;
    }

    @PostMapping("/validate-qr")
    public ResponseEntity<AccessValidationResponse> validateQr(
            @Validated @RequestBody ValidateQrRequest request,
            @AuthenticationPrincipal User staff) {
        return ResponseEntity.ok(accessService.validateQr(request, staff));
    }

    @GetMapping("/virtual/{ticketId}")
    public ResponseEntity<VirtualAccessResponse> getVirtualAccess(
            @PathVariable java.util.UUID ticketId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(accessService.getVirtualAccess(ticketId, user));
    }

    @GetMapping("/my-events")
    public ResponseEntity<java.util.List<org.gcrel.tfbackevent.event.dto.response.EventResponse>> getMyEvents(
            @AuthenticationPrincipal User controller,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(accessService.getMyEvents(controller, pageable));
    }
}
