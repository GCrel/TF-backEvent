package org.gcrel.tfbackevent.controller;

import org.gcrel.tfbackevent.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.dto.response.TicketResponse;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/purchase")
    public ResponseEntity<PurchaseResponse> purchase(
            @RequestBody PurchaseTicketRequest request, Authentication authentication) {
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

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(
            @PathVariable UUID id, Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        TicketResponse response = ticketService.getTicket(id, attendee);
        return ResponseEntity.ok(response);
    }
}
