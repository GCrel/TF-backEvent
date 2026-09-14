package org.gcrel.tfbackevent.service;

import org.gcrel.tfbackevent.dto.request.WebhookPaymentRequest;
import org.gcrel.tfbackevent.exception.WebhookValidationException;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.repository.PaymentRepository;
import org.gcrel.tfbackevent.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class WebhookService {
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final MercadoPagoService mercadoPagoService;
    private final QRService qrService;
    private final EmailService emailService;

    @Value("${MERCADO_PAGO_WEBHOOK_SECRET}")
    private String webhookSecret;

    public WebhookService(
            TicketRepository ticketRepository,
            PaymentRepository paymentRepository,
            MercadoPagoService mercadoPagoService,
            QRService qrService,
            EmailService emailService) {
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.qrService = qrService;
        this.emailService = emailService;
    }

    public void processPaymentNotification(
            WebhookPaymentRequest request, String xSignature, String xRequestId) {
        validateSignature(xSignature, xRequestId, request.getData().getId());

        if (!"payment".equals(request.getType())) {
            return;
        }

        com.mercadopago.resources.payment.Payment mpPayment = mercadoPagoService
                .getPaymentById(request.getData().getId());

        Payment payment = paymentRepository.findByTransactionId(mpPayment.getId().toString()).orElse(null);

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

    private void handleApproved(
            Payment payment, com.mercadopago.resources.payment.Payment mpPayment) {
        Ticket ticket = payment.getTicket();

        ticket.setStatus(TicketStatus.ACTIVE);
        ticketRepository.save(ticket);

        payment.setPaymentStatus(PaymentStatus.APPROVED);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        String qrBase64 = qrService.generateQRBase64(ticket.getId());
        emailService.sendTicketEmail(ticket.getAttendee().getEmail(), ticket, qrBase64);
    }

    private void handleRejected(
            Payment payment, com.mercadopago.resources.payment.Payment mpPayment) {
        Ticket ticket = payment.getTicket();

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        payment.setPaymentStatus(PaymentStatus.REJECTED);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private void validateSignature(String xSignature, String xRequestId, String paymentId) {
        try {
            Map<String, String> signatureParts = extractSignatureData(xSignature);
            String ts = signatureParts.get("ts");
            String receivedSignature = signatureParts.get("v1");

            if (ts == null || receivedSignature == null) {
                throw new WebhookValidationException();
            }

            String message = "id:" + paymentId + ";request-id:" + xRequestId + ";ts:" + ts + ";";

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hash);

            if (!computedSignature.equals(receivedSignature)) {
                throw new WebhookValidationException();
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new WebhookValidationException();
        }
    }

    private Map<String, String> extractSignatureData(String xSignature) {
        Map<String, String> data = new HashMap<>();
        for (String part : xSignature.split(",")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2) {
                data.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return data;
    }
}
