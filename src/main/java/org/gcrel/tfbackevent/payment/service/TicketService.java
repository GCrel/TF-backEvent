package org.gcrel.tfbackevent.payment.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.config.TicketPurchaseProperties;
import org.gcrel.tfbackevent.payment.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse.PurchasedTicket;
import org.gcrel.tfbackevent.payment.dto.response.TicketResponse;
import org.gcrel.tfbackevent.payment.exception.EventCapacityExceededException;
import org.gcrel.tfbackevent.payment.exception.EventPriceChangedException;
import org.gcrel.tfbackevent.payment.exception.InvalidTicketPurchaseException;
import org.gcrel.tfbackevent.payment.exception.PaymentProcessingException;
import org.gcrel.tfbackevent.payment.exception.PaymentRejectedException;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.gcrel.tfbackevent.payment.exception.UnauthorizedTicketAccessException;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketOrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final MercadoPagoService mercadoPagoService;
    private final MpOAuthService mpOAuthService;
    private final PaymentService paymentService;
    private final DeliveryService deliveryService;
    private final QRService qrService;
    private final TransactionTemplate transactionTemplate;
    private final EventWindowProperties eventWindowProperties;
    private final TicketPurchaseProperties purchaseProperties;

    public TicketService(TicketRepository ticketRepository, TicketOrderRepository orderRepository,
            EventRepository eventRepository, EventParticipationRepository eventParticipationRepository,
            MercadoPagoService mercadoPagoService, MpOAuthService mpOAuthService,
            PaymentService paymentService, DeliveryService deliveryService, QRService qrService,
            TransactionTemplate transactionTemplate, EventWindowProperties eventWindowProperties,
            TicketPurchaseProperties purchaseProperties) {
        this.ticketRepository = ticketRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.mpOAuthService = mpOAuthService;
        this.paymentService = paymentService;
        this.deliveryService = deliveryService;
        this.qrService = qrService;
        this.transactionTemplate = transactionTemplate;
        this.eventWindowProperties = eventWindowProperties;
        this.purchaseProperties = purchaseProperties;
    }

    public PurchaseResponse purchase(PurchaseTicketRequest request, User buyer) {
        int quantity = request.quantity();
        if (quantity > purchaseProperties.maxQuantity()) {
            throw new InvalidTicketPurchaseException(
                    "Se pueden comprar como máximo " + purchaseProperties.maxQuantity()
                            + " entradas por orden.");
        }

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new EventNotFoundException(request.eventId()));
        validateEventForPurchase(event);

        if (isFree(event)) {
            return issueFreeOrder(request.eventId(), quantity, buyer);
        }
        if (!request.hasPaymentData()) {
            throw new InvalidTicketPurchaseException(
                    "Los datos de pago son obligatorios para eventos con precio.");
        }

        EventParticipation organizerParticipation = eventParticipationRepository
                .findByEventAndId_EventRole(event, EventRole.ORGANIZER)
                .orElseThrow(EventOrganizerMissingException::new);

        String accessToken = mpOAuthService
                .getValidAccessToken(organizerParticipation.getUser());

        TicketOrder order = transactionTemplate.execute(
                status -> reserveOrder(request.eventId(), quantity, buyer, false));

        com.mercadopago.resources.payment.Payment mpPayment;
        try {
            mpPayment = mercadoPagoService.processPayment(
                    request.cardToken(),
                    order.getTotalAmount(),
                    request.installments(),
                    request.payerEmail(),
                    request.paymentMethodId(),
                    accessToken,
                    order.getId().toString());
        } catch (PaymentRejectedException e) {
            cancelReservation(order);
            throw e;
        } catch (PaymentProcessingException e) {
            // Si el resultado es incierto, la reconciliación resolverá la orden.
            if (!e.isOutcomeUnknown()) {
                cancelReservation(order);
            }
            throw e;
        }

        return paymentService.handleNewPayment(order, mpPayment);
    }

    // Emite las entradas de un evento gratuito sin pasar por MercadoPago.
    private PurchaseResponse issueFreeOrder(UUID eventId, int quantity, User buyer) {
        TicketOrder order = transactionTemplate.execute(
                status -> reserveOrder(eventId, quantity, buyer, true));

        boolean includeQr = order.getEvent().getType() == EventType.IN_PERSON;
        List<PurchasedTicket> purchased = ticketRepository.findByOrder(order).stream()
                .map(ticket -> new PurchasedTicket(
                        ticket.getId(), includeQr ? generateQrOrNull(ticket) : null))
                .toList();

        return new PurchaseResponse(
                order.getId(),
                "approved",
                "free",
                "Entradas emitidas. Te las enviaremos por email.",
                purchased);
    }

    private TicketOrder reserveOrder(UUID eventId, int quantity, User buyer, boolean expectFree) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        validateEventForPurchase(event);
        // El precio pudo cambiar entre la lectura inicial y el bloqueo.
        if (isFree(event) != expectFree) {
            throw new EventPriceChangedException();
        }

        if (event.getType() == EventType.IN_PERSON) {
            InPersonEvent inPerson = (InPersonEvent) Hibernate.unproxy(event);
            long reservedTickets = ticketRepository.countByEventAndStatusIn(
                    event, List.of(TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED));
            if (reservedTickets + quantity > inPerson.getCapacity()) {
                throw new EventCapacityExceededException();
            }
        }

        TicketOrder order = orderRepository.save(
                new TicketOrder(buyer, event, quantity, event.getBasePrice(), LocalDateTime.now()));
        List<Ticket> tickets = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            Ticket ticket = new Ticket(order);
            if (expectFree) {
                ticket.setStatus(TicketStatus.ACTIVE);
            }
            tickets.add(ticket);
        }
        ticketRepository.saveAll(tickets);

        if (expectFree) {
            order.scheduleDelivery(LocalDateTime.now());
        }
        return order;
    }

    private boolean isFree(Event event) {
        return event.getBasePrice().signum() == 0;
    }

    private String generateQrOrNull(Ticket ticket) {
        try {
            return qrService.generateQRBase64(ticket.getId());
        } catch (QRGenerationException ignored) {
            // DeliveryService volverá a generar el QR al enviar el email.
            return null;
        }
    }

    private void cancelReservation(TicketOrder order) {
        ticketRepository.cancelPendingWithoutPayment(order, TicketStatus.PENDING, TicketStatus.CANCELLED);
    }

    private void validateEventForPurchase(Event event) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = event.getDateTime().plusHours(eventWindowProperties.afterHours());
        if (event.getStatus() != EventStatus.PUBLISHED || !now.isBefore(windowEnd)) {
            throw new EventNotAvailableException();
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

        deliveryService.requestDelivery(ticket.getOrder().getId());
    }
}
