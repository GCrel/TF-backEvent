package org.gcrel.tfbackevent.payment.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse.PurchasedTicket;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final TicketRepository ticketRepository;
    private final TicketOrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final QRService qrService;

    public PaymentService(TicketRepository ticketRepository, TicketOrderRepository orderRepository,
            PaymentRepository paymentRepository, QRService qrService) {
        this.ticketRepository = ticketRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.qrService = qrService;
    }

    @Transactional
    public PurchaseResponse handleNewPayment(TicketOrder order,
            com.mercadopago.resources.payment.Payment mpPayment) {
        String status = mpPayment.getStatus();
        PaymentStatus localStatus = switch (status) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected", "cancelled" -> PaymentStatus.REJECTED;
            default -> PaymentStatus.PENDING;
        };

        TicketStatus ticketStatus = localStatus == PaymentStatus.APPROVED
                ? TicketStatus.ACTIVE
                : localStatus == PaymentStatus.REJECTED ? TicketStatus.CANCELLED : TicketStatus.PENDING;
        List<Ticket> tickets = ticketRepository.findByOrder(order);
        tickets.forEach(ticket -> ticket.setStatus(ticketStatus));
        ticketRepository.saveAll(tickets);

        paymentRepository.save(createPayment(order, mpPayment, localStatus));

        if (localStatus == PaymentStatus.APPROVED) {
            order.scheduleDelivery(LocalDateTime.now());
            orderRepository.save(order);
        }

        boolean includeQr = localStatus == PaymentStatus.APPROVED
                && order.getEvent().getType() == EventType.IN_PERSON;
        List<PurchasedTicket> purchased = tickets.stream()
                .map(ticket -> new PurchasedTicket(
                        ticket.getId(), includeQr ? generateQrOrNull(ticket) : null))
                .toList();

        return new PurchaseResponse(
                order.getId(),
                status,
                mpPayment.getStatusDetail(),
                localStatus == PaymentStatus.APPROVED
                        ? "Pago aprobado. Tus entradas serán enviadas por email."
                        : localStatus == PaymentStatus.REJECTED
                                ? "El pago fue rechazado. Intentá con otro medio de pago."
                                : "Tu pago está siendo procesado. Te avisaremos por email cuando se confirme.",
                purchased);
    }

    @Transactional
    public void handleExistingPayment(Payment payment,
            com.mercadopago.resources.payment.Payment mpPayment) {
        Payment managedPayment = paymentRepository.findByTransactionId(payment.getTransactionId())
                .orElse(payment);
        if (managedPayment.getPaymentStatus() != PaymentStatus.PENDING) {
            return;
        }

        managedPayment.setMercadoPagoStatus(mpPayment.getStatus());
        managedPayment.setStatusDetail(mpPayment.getStatusDetail());

        switch (mpPayment.getStatus()) {
            case "approved" -> approve(managedPayment);
            case "rejected", "cancelled" -> reject(managedPayment);
            default -> {
                managedPayment.setNextReconcileAt(LocalDateTime.now().plus(reconciliationDelay(managedPayment)));
                paymentRepository.save(managedPayment);
            }
        }
    }

    static Duration reconciliationDelay(Payment payment) {
        Duration delay = Duration.between(payment.getPaymentDate(), LocalDateTime.now()).dividedBy(4);
        if (delay.compareTo(Duration.ofMinutes(5)) < 0) {
            return Duration.ofMinutes(5);
        }
        return delay.compareTo(Duration.ofHours(24)) > 0
                ? Duration.ofHours(24)
                : delay;
    }

    private String generateQrOrNull(Ticket ticket) {
        try {
            return qrService.generateQRBase64(ticket.getId());
        } catch (QRGenerationException ignored) {
            // DeliveryService volverá a generar el QR al enviar el email.
            return null;
        }
    }

    private Payment createPayment(TicketOrder order,
            com.mercadopago.resources.payment.Payment mpPayment, PaymentStatus status) {
        Payment payment = new Payment(
                mpPayment.getId().toString(),
                mpPayment.getTransactionAmount(),
                LocalDateTime.now(),
                order);
        payment.setPaymentStatus(status);
        payment.setMercadoPagoStatus(mpPayment.getStatus());
        payment.setStatusDetail(mpPayment.getStatusDetail());
        return payment;
    }

    private void approve(Payment payment) {
        updatePendingTickets(payment.getOrder(), TicketStatus.ACTIVE);
        payment.setPaymentStatus(PaymentStatus.APPROVED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setNextReconcileAt(null);
        paymentRepository.save(payment);

        TicketOrder order = payment.getOrder();
        order.scheduleDelivery(LocalDateTime.now());
        orderRepository.save(order);
    }

    private void reject(Payment payment) {
        updatePendingTickets(payment.getOrder(), TicketStatus.CANCELLED);
        payment.setPaymentStatus(PaymentStatus.REJECTED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setNextReconcileAt(null);
        paymentRepository.save(payment);

        TicketOrder order = payment.getOrder();
        order.cancelDelivery();
        orderRepository.save(order);
    }

    private void updatePendingTickets(TicketOrder order, TicketStatus newStatus) {
        List<Ticket> tickets = ticketRepository.findByOrderAndStatus(order, TicketStatus.PENDING);
        tickets.forEach(ticket -> ticket.setStatus(newStatus));
        ticketRepository.saveAll(tickets);
    }
}
