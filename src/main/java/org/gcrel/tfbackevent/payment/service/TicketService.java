package org.gcrel.tfbackevent.payment.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.event.config.EventWindowProperties;
import org.gcrel.tfbackevent.event.exception.EventNotAvailableException;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.exception.EventOrganizerMissingException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.dto.response.TicketResponse;
import org.gcrel.tfbackevent.payment.exception.EventCapacityExceededException;
import org.gcrel.tfbackevent.payment.exception.InvalidTicketPurchaseException;
import org.gcrel.tfbackevent.payment.exception.PaymentProcessingException;
import org.gcrel.tfbackevent.payment.exception.PaymentRejectedException;
import org.gcrel.tfbackevent.payment.exception.TicketAlreadyPurchasedException;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.gcrel.tfbackevent.payment.exception.UnauthorizedTicketAccessException;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final MercadoPagoService mercadoPagoService;
    private final MpOAuthService mpOAuthService;
    private final PaymentService paymentService;
    private final QRService qrService;
    private final TransactionTemplate transactionTemplate;
    private final EventWindowProperties eventWindowProperties;

    public TicketService(TicketRepository ticketRepository, EventRepository eventRepository,
            EventParticipationRepository eventParticipationRepository, MercadoPagoService mercadoPagoService,
            MpOAuthService mpOAuthService, PaymentService paymentService, QRService qrService,
            TransactionTemplate transactionTemplate, EventWindowProperties eventWindowProperties) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.mpOAuthService = mpOAuthService;
        this.paymentService = paymentService;
        this.qrService = qrService;
        this.transactionTemplate = transactionTemplate;
        this.eventWindowProperties = eventWindowProperties;
    }

    public PurchaseResponse purchase(PurchaseTicketRequest request, User attendee) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new EventNotFoundException(request.eventId()));
        validateEventForPurchase(event);

        EventParticipation organizerParticipation = eventParticipationRepository
                .findByEventAndId_EventRole(event, EventRole.ORGANIZER)
                .orElseThrow(EventOrganizerMissingException::new);

        String accessToken = mpOAuthService
                .getValidAccessToken(organizerParticipation.getUser());

        Ticket ticket = transactionTemplate.execute(status -> reserveTicket(request, attendee));
        event = ticket.getEvent();

        com.mercadopago.resources.payment.Payment mpPayment;
        try {
            mpPayment = mercadoPagoService.processPayment(
                    request.cardToken(),
                    event.getBasePrice(),
                    request.installments(),
                    request.payerEmail(),
                    request.paymentMethodId(),
                    accessToken,
                    ticket.getId().toString());
        } catch (PaymentRejectedException e) {
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.save(ticket);
            throw e;
        } catch (PaymentProcessingException e) {
            if (!e.isOutcomeUnknown()) {
                ticket.setStatus(TicketStatus.CANCELLED);
                ticketRepository.save(ticket);
            }
            throw e;
        }

        return paymentService.handleNewPayment(ticket, mpPayment, attendee);
    }

    private Ticket reserveTicket(PurchaseTicketRequest request, User attendee) {
        Event event = eventRepository.findByIdForUpdate(request.eventId())
                .orElseThrow(() -> new EventNotFoundException(request.eventId()));

        validateEventForPurchase(event);

        List<TicketStatus> activeStatuses = List.of(TicketStatus.PENDING, TicketStatus.ACTIVE);
        if (ticketRepository.existsByEventAndAttendeeAndStatusIn(event, attendee, activeStatuses)) {
            throw new TicketAlreadyPurchasedException();
        }

        if (event.getType() == EventType.IN_PERSON) {
            InPersonEvent inPerson = (InPersonEvent) Hibernate.unproxy(event);
            long reservedTickets = ticketRepository.countByEventAndStatusIn(
                    event, List.of(TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED));
            if (reservedTickets >= inPerson.getCapacity()) {
                throw new EventCapacityExceededException();
            }
        }

        return ticketRepository.save(new Ticket(LocalDateTime.now(), event, attendee));
    }

    private void validateEventForPurchase(Event event) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = event.getDateTime().plusHours(eventWindowProperties.afterHours());
        if (event.getStatus() != EventStatus.PUBLISHED || !now.isBefore(windowEnd)) {
            throw new EventNotAvailableException();
        }
        if (event.getBasePrice() == null || event.getBasePrice().signum() <= 0) {
            throw new InvalidTicketPurchaseException("El evento debe tener un precio mayor que cero.");
        }
    }

    public List<TicketResponse> getMyTickets(User attendee) {
        return ticketRepository.findByAttendee(attendee).stream()
                .map(ticket -> {
                    String qr = generateQrForInPersonTicket(ticket);
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

        String qr = generateQrForInPersonTicket(ticket);

        return new TicketResponse(ticket, qr);
    }

    private String generateQrForInPersonTicket(Ticket ticket) {
        return ticket.getStatus() == TicketStatus.ACTIVE
                && ticket.getEvent().getType() == EventType.IN_PERSON
                ? qrService.generateQRBase64(ticket.getId())
                : null;
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
