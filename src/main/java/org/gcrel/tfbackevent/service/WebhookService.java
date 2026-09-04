package org.gcrel.tfbackevent.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.gcrel.tfbackevent.dto.request.WebhookPaymentRequest;
import org.gcrel.tfbackevent.exception.WebhookValidationException;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.repository.PaymentRepository;
import org.gcrel.tfbackevent.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final MercadoPagoService mercadoPagoService;
    private final QRService qrService;
    private final EmailService emailService;

    @Value("${mercadopago.webhook-secret}")
    private String webhookSecret;

    public WebhookService(TicketRepository ticketRepository, PaymentRepository paymentRepository,
            MercadoPagoService mercadoPagoService, QRService qrService, EmailService emailService) {
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.qrService = qrService;
        this.emailService = emailService;
    }

    public void processPaymentNotification(WebhookPaymentRequest request, String xSignature, String xRequestId) {
        validateSignature(xSignature, xRequestId, request.getData().getId());

        if (!request.getAction().startsWith("payment.")) {
            return;
        }

        com.mercadopago.resources.payment.Payment mpPayment = mercadoPagoService
                .getPaymentById(request.getData().getId());

        Payment payment = paymentRepository
                .findByTransactionId(mpPayment.getId().toString())
                .orElse(null);

        if (payment == null) {
            return;
        }

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            return;
        }

        switch (mpPayment.getStatus()) {
            case "approved" -> handleApproved(payment, mpPayment);
            case "rejected", "cancelled" -> handleRejected(payment, mpPayment);
        }
    }

    private void handleApproved(Payment payment, com.mercadopago.resources.payment.Payment mpPayment) {
        Ticket ticket = payment.getTicket();

        ticket.setStatus(TicketStatus.ACTIVE);
        ticketRepository.save(ticket);

        payment.setPaymentStatus(PaymentStatus.APPROVED);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        String qrBase64 = qrService.generateQRBase64(ticket.getId());
        emailService.sendTicketEmail(ticket.getAttendee().getEmail(), ticket, qrBase64);
    }

    private void handleRejected(Payment payment, com.mercadopago.resources.payment.Payment mpPayment) {
        Ticket ticket = payment.getTicket();

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        payment.setPaymentStatus(PaymentStatus.REJECTED);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private void validateSignature(String xSignature, String xRequestId, String paymentId) {
        try {
            String message = "id:" + paymentId + ";request-id:" + xRequestId + ";";

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hash);

            String receivedSignature = extractSignatureValue(xSignature);

            if (!computedSignature.equals(receivedSignature)) {
                throw new WebhookValidationException();
            }

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new WebhookValidationException();
        }
    }

    private String extractSignatureValue(String xSignature) {
        // Formato: "ts=1704067200,v1=abc123..."
        for (String part : xSignature.split(",")) {
            if (part.startsWith("v1=")) {
                return part.substring(3);
            }
        }
        throw new WebhookValidationException();
    }
}
