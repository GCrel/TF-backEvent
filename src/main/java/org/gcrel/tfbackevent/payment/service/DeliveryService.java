package org.gcrel.tfbackevent.payment.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.auth.service.dto.TicketQr;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.exception.TicketDeliveryException;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {
    private static final long DELIVERY_RESEND_COOLDOWN_MINUTES = 5;
    private final TicketOrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final QRService qrService;
    private final EmailService emailService;

    public DeliveryService(TicketOrderRepository orderRepository, TicketRepository ticketRepository,
            QRService qrService, EmailService emailService) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.qrService = qrService;
        this.emailService = emailService;
    }

    // cooldown 5
    @Transactional
    public void requestDelivery(UUID orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            boolean cooldownElapsed = order.getNextDeliveryAttemptAt() == null
                    || !LocalDateTime.now().isBefore(order.getNextDeliveryAttemptAt());
            if (order.getDeliveryStatus() != DeliveryStatus.SENT || cooldownElapsed) {
                order.scheduleDelivery(LocalDateTime.now());
                orderRepository.save(order);
            }
        });
    }

    @Scheduled(fixedDelayString = "${ticket.delivery.retry-delay-ms:5000}")
    public void processDueDeliveries() {
        recoverStaleDeliveries();
        List<TicketOrder> orders = orderRepository
                .findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
                        List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED), LocalDateTime.now());
        orders.forEach(this::deliver);
    }

    private void recoverStaleDeliveries() {
        orderRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                DeliveryStatus.PROCESSING, LocalDateTime.now().minusMinutes(10))
                .forEach(order -> {
                    order.setDeliveryStatus(DeliveryStatus.FAILED);
                    order.setNextDeliveryAttemptAt(LocalDateTime.now());
                    orderRepository.save(order);
                });
    }

    private void deliver(TicketOrder order) {
        order.setDeliveryStatus(DeliveryStatus.PROCESSING);
        order.setDeliveryAttempts(order.getDeliveryAttempts() + 1);
        order.setNextDeliveryAttemptAt(LocalDateTime.now().plusMinutes(10));
        // saveAndFlush devuelve la instancia con la versión actualizada; seguir usando
        // la instancia original provocaría un conflicto de bloqueo optimista al
        // guardar.
        TicketOrder claimed = orderRepository.saveAndFlush(order);

        List<Ticket> tickets = ticketRepository.findByOrderAndStatus(claimed, TicketStatus.ACTIVE);
        try {
            if (tickets.isEmpty()) {
                claimed.cancelDelivery();
                orderRepository.save(claimed);
                return;
            }
            String to = order.getBuyer().getEmail();
            if (order.getEvent().getType() == EventType.VIRTUAL) {
                emailService.sendVirtualTicketEmail(to, order, tickets.size());
            } else {
                List<TicketQr> qrs = tickets.stream()
                        .map(ticket -> new TicketQr(ticket.getId(), qrService.generateQRBase64(ticket.getId())))
                        .toList();
                emailService.sendTicketEmail(to, order, qrs);
            }
            claimed.setDeliveryStatus(DeliveryStatus.SENT);
            claimed.setLastDeliveryError(null);
            claimed.setNextDeliveryAttemptAt(
                    LocalDateTime.now().plusMinutes(DELIVERY_RESEND_COOLDOWN_MINUTES));
        } catch (QRGenerationException | TicketDeliveryException e) {
            claimed.setDeliveryStatus(DeliveryStatus.FAILED);
            claimed.setLastDeliveryError(e.getMessage());
            claimed.setNextDeliveryAttemptAt(LocalDateTime.now().plusMinutes(1));
        }
        orderRepository.save(claimed);
    }
}
