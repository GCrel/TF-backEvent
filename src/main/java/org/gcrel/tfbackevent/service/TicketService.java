package org.gcrel.tfbackevent.service;

import org.gcrel.tfbackevent.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.dto.response.TicketResponse;
import org.gcrel.tfbackevent.exception.*;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.repository.EventRepository;
import org.gcrel.tfbackevent.repository.PaymentRepository;
import org.gcrel.tfbackevent.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final EventRepository eventRepository;
    private final MercadoPagoService mercadoPagoService;
    private final QRService qrService;
    private final EmailService emailService;

    public TicketService(TicketRepository ticketRepository, PaymentRepository paymentRepository, EventRepository eventRepository, MercadoPagoService mercadoPagoService, QRService qrService, EmailService emailService) {
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.qrService = qrService;
        this.emailService = emailService;
    }

    @Transactional
    public PurchaseResponse purchase(PurchaseTicketRequest request, User attendee) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new EventNotFoundException(request.getEventId()));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotAvailableException();
        }

        Ticket ticket = new Ticket(LocalDateTime.now(), event, attendee);
        ticketRepository.save(ticket);

        com.mercadopago.resources.payment.Payment mpPayment;
        try {
            mpPayment = mercadoPagoService.processPayment(
                    request.getCardToken(),
                    event.getBasePrice(),
                    request.getInstallments(),
                    request.getPayerEmail(),
                    request.getPaymentMethodId()
            );
        } catch (PaymentRejectedException e) {
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.save(ticket);
            throw e;
        }

        return switch (mpPayment.getStatus()) {
            case "approved"             -> handleApproved(ticket, mpPayment, attendee);
            case "rejected", "cancelled"-> handleRejected(ticket, mpPayment);
            default                     -> handlePending(ticket, mpPayment);
        };
    }

    public List<TicketResponse> getMyTickets(User attendee) {
        return ticketRepository.findByAttendee(attendee).stream().map(ticket -> {
            String qr = ticket.getStatus() == TicketStatus.ACTIVE ? qrService.generateQRBase64(ticket.getId()) : null;
            return new TicketResponse(ticket, qr);
        }).toList();
    }

    public TicketResponse getTicket(UUID id, User attendee) {
        Ticket ticket = ticketRepository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));

        if (!ticket.getAttendee().getId().equals(attendee.getId())) {
            throw new UnauthorizedTicketAccessException();
        }

        String qr = ticket.getStatus() == TicketStatus.ACTIVE ? qrService.generateQRBase64(ticket.getId()) : null;

        return new TicketResponse(ticket, qr);
    }

    private PurchaseResponse handleApproved(Ticket ticket, com.mercadopago.resources.payment.Payment mpPayment, User attendee) {
        ticket.setStatus(TicketStatus.ACTIVE);
        ticketRepository.save(ticket);

        Payment payment = new Payment(
                mpPayment.getId().toString(),
                mpPayment.getTransactionAmount(),
                LocalDateTime.now(),
                ticket
        );
        payment.setPaymentStatus(PaymentStatus.APPROVED);
        paymentRepository.save(payment);

        String qrBase64 = qrService.generateQRBase64(ticket.getId());

        emailService.sendTicketEmail(attendee.getEmail(), ticket, qrBase64);

        return new PurchaseResponse(
                ticket.getId(),
                "approved",
                "Pago aprobado. Tu entrada fue enviada por email.",
                qrBase64
        );
    }

    private PurchaseResponse handleRejected(Ticket ticket, com.mercadopago.resources.payment.Payment mpPayment) {
        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        Payment payment = new Payment(mpPayment.getId().toString(), mpPayment.getTransactionAmount(), LocalDateTime.now(), ticket);
        payment.setPaymentStatus(PaymentStatus.REJECTED);
        paymentRepository.save(payment);

        return new PurchaseResponse(ticket.getId(), "rejected", "El pago fue rechazado. Intentá con otro medio de pago.", null);
    }

    private PurchaseResponse handlePending(Ticket ticket, com.mercadopago.resources.payment.Payment mpPayment) {
        Payment payment = new Payment(
                mpPayment.getId().toString(),
                mpPayment.getTransactionAmount(),
                LocalDateTime.now(),
                ticket
        );
        payment.setPaymentStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        return new PurchaseResponse(
                ticket.getId(),
                "pending",
                "Tu pago está siendo procesado. Te avisaremos por email cuando se confirme.",
                null
        );
    }
}
