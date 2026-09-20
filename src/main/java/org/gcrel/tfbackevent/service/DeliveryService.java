package org.gcrel.tfbackevent.service;

import java.time.LocalDateTime;
import java.util.List;

import org.gcrel.tfbackevent.exception.QRGenerationException;
import org.gcrel.tfbackevent.exception.TicketDeliveryException;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.repository.PaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DeliveryService {
    private final PaymentRepository paymentRepository;
    private final QRService qrService;
    private final EmailService emailService;

    public DeliveryService(PaymentRepository paymentRepository, QRService qrService, EmailService emailService) {
        this.paymentRepository = paymentRepository;
        this.qrService = qrService;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelayString = "${ticket.delivery.retry-delay-ms:5000}")
    public void processDueDeliveries() {
        recoverStaleDeliveries();
        List<Payment> payments = paymentRepository
                .findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
                        List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED), LocalDateTime.now());
        payments.forEach(this::deliver);
    }

    private void recoverStaleDeliveries() {
        paymentRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                DeliveryStatus.PROCESSING, LocalDateTime.now().minusMinutes(10))
                .forEach(payment -> {
                    payment.setDeliveryStatus(DeliveryStatus.FAILED);
                    payment.setNextDeliveryAttemptAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                });
    }

    private void deliver(Payment payment) {
        payment.setDeliveryStatus(DeliveryStatus.PROCESSING);
        payment.setDeliveryAttempts(payment.getDeliveryAttempts() + 1);
        payment.setNextDeliveryAttemptAt(LocalDateTime.now().plusMinutes(10));
        paymentRepository.saveAndFlush(payment);

        try {
            String qr = qrService.generateQRBase64(payment.getTicket().getId());
            emailService.sendTicketEmail(payment.getTicket().getAttendee().getEmail(), payment.getTicket(), qr);
            payment.setDeliveryStatus(DeliveryStatus.SENT);
            payment.setLastDeliveryError(null);
        } catch (QRGenerationException | TicketDeliveryException e) {
            payment.setDeliveryStatus(DeliveryStatus.FAILED);
            payment.setLastDeliveryError(e.getMessage());
            payment.setNextDeliveryAttemptAt(LocalDateTime.now().plusMinutes(1));
        }
        paymentRepository.save(payment);
    }
}
