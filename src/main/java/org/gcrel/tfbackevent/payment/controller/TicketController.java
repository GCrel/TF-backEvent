package org.gcrel.tfbackevent.payment.controller;

import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.payment.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.dto.response.TicketResponse;
import org.gcrel.tfbackevent.payment.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/purchase")
    public ResponseEntity<PurchaseResponse> purchase(
            @Validated @RequestBody PurchaseTicketRequest request, Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        PurchaseResponse response = ticketService.purchase(request, attendee);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-tickets")
    public ResponseEntity<List<TicketResponse>> getMyTickets(Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        List<TicketResponse> response = ticketService.getMyTickets(attendee);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/resend-email")
    public ResponseEntity<Void> resendEmail(@PathVariable UUID id, Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        ticketService.resendTicketEmail(id, attendee);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(
            @PathVariable UUID id, Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        TicketResponse response = ticketService.getTicket(id, attendee);
        return ResponseEntity.ok(response);
    }
}
