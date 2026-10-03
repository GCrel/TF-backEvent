package org.gcrel.tfbackevent.payment.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final long DELIVERY_RESEND_COOLDOWN_MINUTES = 5;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final QRService qrService;

    public PaymentService(TicketRepository ticketRepository, PaymentRepository paymentRepository,
            QRService qrService) {
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.qrService = qrService;
    }

    @Transactional
    public PurchaseResponse handleNewPayment(Ticket ticket,
            com.mercadopago.resources.payment.Payment mpPayment, User attendee) {
        String status = mpPayment.getStatus();
        PaymentStatus localStatus = switch (status) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected", "cancelled" -> PaymentStatus.REJECTED;
            default -> PaymentStatus.PENDING;
        };

        ticket.setStatus(localStatus == PaymentStatus.APPROVED
                ? TicketStatus.ACTIVE
                : localStatus == PaymentStatus.REJECTED ? TicketStatus.CANCELLED : TicketStatus.PENDING);
        Ticket managedTicket = ticketRepository.save(ticket);

        Payment payment = createPayment(managedTicket, mpPayment, localStatus);
        paymentRepository.save(payment);

        String qr = null;
        if (localStatus == PaymentStatus.APPROVED
                && managedTicket.getEvent().getType() == EventType.IN_PERSON) {
            try {
                qr = qrService.generateQRBase64(managedTicket.getId());
            } catch (QRGenerationException ignored) {
                // DeliveryService will retry QR generation after the transaction commits.
            }
        }

        return new PurchaseResponse(
                managedTicket.getId(),
                status,
                mpPayment.getStatusDetail(),
                localStatus == PaymentStatus.APPROVED
                        ? "Pago aprobado. Tu entrada será enviada por email."
                        : localStatus == PaymentStatus.REJECTED
                                ? "El pago fue rechazado. Intentá con otro medio de pago."
                                : "Tu pago está siendo procesado. Te avisaremos por email cuando se confirme.",
                qr);
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

    @Transactional
    public void requestDelivery(Ticket ticket) {
        paymentRepository.findByTicket(ticket).ifPresent(payment -> {
            boolean cooldownElapsed = payment.getNextDeliveryAttemptAt() == null
                    || !LocalDateTime.now().isBefore(payment.getNextDeliveryAttemptAt());
            if (payment.getPaymentStatus() == PaymentStatus.APPROVED
                    && (payment.getDeliveryStatus() != DeliveryStatus.SENT
                            || cooldownElapsed)) {
                payment.setDeliveryStatus(DeliveryStatus.PENDING);
                payment.setNextDeliveryAttemptAt(LocalDateTime.now());
                paymentRepository.save(payment);
            }
        });
    }

    private Payment createPayment(Ticket ticket,
            com.mercadopago.resources.payment.Payment mpPayment, PaymentStatus status) {
        Payment payment = new Payment(
                mpPayment.getId().toString(),
                mpPayment.getTransactionAmount(),
                LocalDateTime.now(),
                ticket);
        payment.setPaymentStatus(status);
        payment.setMercadoPagoStatus(mpPayment.getStatus());
        payment.setStatusDetail(mpPayment.getStatusDetail());
        if (status == PaymentStatus.APPROVED) {
            payment.setDeliveryStatus(DeliveryStatus.PENDING);
            payment.setNextDeliveryAttemptAt(LocalDateTime.now());
        }
        return payment;
    }

    private void approve(Payment payment) {
        payment.getTicket().setStatus(TicketStatus.ACTIVE);
        ticketRepository.save(payment.getTicket());
        payment.setPaymentStatus(PaymentStatus.APPROVED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setNextReconcileAt(null);
        payment.setDeliveryStatus(DeliveryStatus.PENDING);
        payment.setNextDeliveryAttemptAt(LocalDateTime.now());
        payment.setLastDeliveryError(null);
        paymentRepository.save(payment);
    }

    private void reject(Payment payment) {
        payment.getTicket().setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(payment.getTicket());
        payment.setPaymentStatus(PaymentStatus.REJECTED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setNextReconcileAt(null);
        payment.setDeliveryStatus(DeliveryStatus.NOT_REQUIRED);
        payment.setNextDeliveryAttemptAt(null);
        paymentRepository.save(payment);
    }
}
