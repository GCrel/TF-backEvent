package org.gcrel.tfbackevent.payment.service;

import java.time.Duration;
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
import org.gcrel.tfbackevent.payment.config.TicketDeliveryProperties;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.exception.TicketDeliveryException;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DeliveryService {
    private static final long DELIVERY_RESEND_COOLDOWN_MINUTES = 5;
    private final TicketOrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final QRService qrService;
    private final EmailService emailService;
    private final TicketDeliveryProperties deliveryProperties;

    public DeliveryService(TicketOrderRepository orderRepository, TicketRepository ticketRepository,
            QRService qrService, EmailService emailService, TicketDeliveryProperties deliveryProperties) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.qrService = qrService;
        this.emailService = emailService;
        this.deliveryProperties = deliveryProperties;
    }

    // cooldown 5
    @Transactional
    public void requestDelivery(UUID orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            boolean cooldownElapsed = order.getNextDeliveryAttemptAt() == null
                    || !LocalDateTime.now().isBefore(order.getNextDeliveryAttemptAt());
            // PROCESSING: hay un envío en curso; reprogramarlo duplicaría el email.
            if (order.getDeliveryStatus() == DeliveryStatus.PROCESSING) {
                return;
            }
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
        for (TicketOrder order : orders) {
            try {
                deliver(order);
            } catch (RuntimeException e) {
                // Un error inesperado no debe frenar el resto del lote; la orden queda en
                // PROCESSING y recoverStaleDeliveries la reprograma.
                log.error("Error inesperado al entregar la orden {}", order.getId(), e);
            }
        }
    }

    private void recoverStaleDeliveries() {
        orderRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                DeliveryStatus.PROCESSING, LocalDateTime.now().minusMinutes(10))
                .forEach(order -> {
                    registerFailure(order, "La entrega quedó interrumpida.");
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
            registerFailure(claimed, e.getMessage());
        }
        orderRepository.save(claimed);
    }

    // Programa el próximo reintento con backoff exponencial o, si se agotaron los
    // intentos, deja la orden en FAILED sin próxima fecha (solo se reintenta si el
    // comprador pide el reenvío).
    private void registerFailure(TicketOrder order, String error) {
        order.setDeliveryStatus(DeliveryStatus.FAILED);
        order.setLastDeliveryError(error);
        if (order.getDeliveryAttempts() >= deliveryProperties.maxAttempts()) {
            order.setNextDeliveryAttemptAt(null);
            log.warn("Se agotaron los intentos de entrega de la orden {}: {}", order.getId(), error);
        } else {
            order.setNextDeliveryAttemptAt(LocalDateTime.now().plus(backoff(order.getDeliveryAttempts())));
        }
    }

    private Duration backoff(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 30);
        long minutes = deliveryProperties.initialBackoffMinutes() << exponent;
        return Duration.ofMinutes(Math.min(minutes, deliveryProperties.maxBackoffMinutes()));
    }
}
