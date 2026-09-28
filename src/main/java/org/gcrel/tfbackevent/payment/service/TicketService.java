package org.gcrel.tfbackevent.payment.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.event.exception.EventNotAvailableException;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.dto.response.TicketResponse;
import org.gcrel.tfbackevent.payment.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.payment.exception.PaymentRejectedException;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.gcrel.tfbackevent.payment.exception.UnauthorizedTicketAccessException;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final MercadoPagoService mercadoPagoService;
    private final MpOAuthService mpOAuthService;
    private final PaymentService paymentService;
    private final QRService qrService;

    public TicketService(TicketRepository ticketRepository, EventRepository eventRepository,
            EventParticipationRepository eventParticipationRepository, MercadoPagoService mercadoPagoService,
            MpOAuthService mpOAuthService, PaymentService paymentService, QRService qrService) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.mpOAuthService = mpOAuthService;
        this.paymentService = paymentService;
        this.qrService = qrService;
    }

    public PurchaseResponse purchase(PurchaseTicketRequest request, User attendee) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new EventNotFoundException(request.getEventId()));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotAvailableException();
        }

        EventParticipation organizerParticipation = eventParticipationRepository
                .findByEventAndEventRole(event, EventRole.ORGANIZER)
                .orElseThrow(MpAccountNotLinkedException::new);

        String accessToken = mpOAuthService
                .getValidAccessToken(organizerParticipation.getUser());

        Ticket ticket = new Ticket(LocalDateTime.now(), event, attendee);
        ticketRepository.save(ticket);

        com.mercadopago.resources.payment.Payment mpPayment;
        try {
            mpPayment = mercadoPagoService.processPayment(
                    request.getCardToken(),
                    event.getBasePrice(),
                    request.getInstallments(),
                    request.getPayerEmail(),
                    request.getPaymentMethodId(),
                    accessToken);
        } catch (PaymentRejectedException e) {
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.save(ticket);
            throw e;
        }

        return paymentService.handleNewPayment(ticket, mpPayment, attendee);
    }

    public List<TicketResponse> getMyTickets(User attendee) {
        return ticketRepository.findByAttendee(attendee).stream()
                .map(ticket -> {
                    String qr = ticket.getStatus() == TicketStatus.ACTIVE
                            ? qrService.generateQRBase64(ticket.getId())
                            : null;
                    return new TicketResponse(ticket, qr);
                })
                .toList();
    }

    public TicketResponse getTicket(UUID id, User attendee) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        if (!ticket.getAttendee().getId().equals(attendee.getId())) {
            throw new UnauthorizedTicketAccessException();
        }

        String qr = ticket.getStatus() == TicketStatus.ACTIVE
                ? qrService.generateQRBase64(ticket.getId())
                : null;

        return new TicketResponse(ticket, qr);
    }

    public void resendTicketEmail(UUID id, User attendee) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        if (!ticket.getAttendee().getId().equals(attendee.getId())) {
            throw new UnauthorizedTicketAccessException();
        }

        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new TicketNotActiveException();
        }

        paymentService.requestDelivery(ticket);
    }
}
