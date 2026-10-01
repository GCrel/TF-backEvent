package org.gcrel.tfbackevent.payment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentRecoveryService {
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final MpOAuthService mpOAuthService;
    private final MercadoPagoService mercadoPagoService;
    private final PaymentService paymentService;

    @Value("${payment.recovery.pending-after-minutes:5}")
    private long pendingAfterMinutes;

    @Value("${payment.recovery.orphan-after-minutes:15}")
    private long orphanAfterMinutes;

    public PaymentRecoveryService(PaymentRepository paymentRepository, TicketRepository ticketRepository,
            EventParticipationRepository eventParticipationRepository, MpOAuthService mpOAuthService,
            MercadoPagoService mercadoPagoService, PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.ticketRepository = ticketRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.mpOAuthService = mpOAuthService;
        this.mercadoPagoService = mercadoPagoService;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${payment.recovery.retry-delay-ms:60000}")
    public void recoverPayments() {
        reconcilePendingPayments();
        cancelOrphanTickets();
    }

    private void reconcilePendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<Payment> payments = paymentRepository.findPendingForReconciliation(
                PaymentStatus.PENDING, now,
                now.minusMinutes(pendingAfterMinutes),
                PageRequest.of(0, 50));
        for (Payment payment : payments) {
            try {
                var organizer = eventParticipationRepository
                        .findByEventAndEventRole(payment.getTicket().getEvent(), EventRole.ORGANIZER)
                        .orElseThrow();
                String token = mpOAuthService.getValidAccessToken(organizer.getUser());
                var mpPayment = mercadoPagoService.getPaymentById(payment.getTransactionId(), token);
                paymentService.handleExistingPayment(payment, mpPayment);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("El pago {} ya fue reconciliado por otro proceso", payment.getTransactionId());
            } catch (RuntimeException e) {
                paymentRepository.postponeReconciliation(
                        payment.getId(), now.plus(PaymentService.reconciliationDelay(payment)));
                log.warn("No se pudo reconciliar el pago {}; se reintentará luego: {}",
                        payment.getTransactionId(), e.getMessage());
            }
        }
    }

    protected void cancelOrphanTickets() {
        List<org.gcrel.tfbackevent.model.Ticket> tickets = ticketRepository.findPendingOrphans(
                TicketStatus.PENDING, LocalDateTime.now().minusMinutes(orphanAfterMinutes));
        for (var ticket : tickets) {
            try {
                var organizer = eventParticipationRepository
                        .findByEventAndEventRole(ticket.getEvent(), EventRole.ORGANIZER)
                        .orElseThrow();
                String token = mpOAuthService.getValidAccessToken(organizer.getUser());
                var mpPayment = mercadoPagoService.findPaymentByExternalReference(
                        ticket.getId().toString(), token);
                if (mpPayment.isPresent()) {
                    paymentService.handleNewPayment(ticket, mpPayment.get(), ticket.getAttendee());
                } else {
                    ticketRepository.cancelIfPendingWithoutPayment(
                            ticket.getId(), TicketStatus.PENDING, TicketStatus.CANCELLED);
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("El ticket {} ya fue procesado por otro proceso", ticket.getId());
            } catch (RuntimeException e) {
                log.warn("No se pudo reconciliar el ticket huérfano {}", ticket.getId(), e);
            }
        }
    }
}
