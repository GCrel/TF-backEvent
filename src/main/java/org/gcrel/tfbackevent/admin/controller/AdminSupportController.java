package org.gcrel.tfbackevent.admin.controller;

import java.util.UUID;

import org.gcrel.tfbackevent.admin.dto.response.PaymentAdminResponse;
import org.gcrel.tfbackevent.admin.dto.response.TicketAdminResponse;
import org.gcrel.tfbackevent.admin.service.AdminSupportService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSupportController {
    private final AdminSupportService adminSupportService;

    public AdminSupportController(AdminSupportService adminSupportService) {
        this.adminSupportService = adminSupportService;
    }

    @GetMapping("/payments")
    public ResponseEntity<Page<PaymentAdminResponse>> searchPayments(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) PaymentStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(adminSupportService.searchPayments(userId, eventId, status, pageable));
    }

    @PostMapping("/payments/{id}/reconcile")
    public ResponseEntity<PaymentAdminResponse> reconcile(
            @PathVariable UUID id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(adminSupportService.reconcile(actor, id));
    }

    @GetMapping("/tickets")
    public ResponseEntity<Page<TicketAdminResponse>> searchTickets(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) TicketStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(adminSupportService.searchTickets(userId, eventId, status, pageable));
    }

    @PostMapping("/tickets/{id}/resend-delivery")
    public ResponseEntity<Void> resendDelivery(
            @PathVariable UUID id, @AuthenticationPrincipal User actor) {
        adminSupportService.resendDelivery(actor, id);
        return ResponseEntity.noContent().build();
    }
}
