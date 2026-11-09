package org.gcrel.tfbackevent.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.payment.dto.request.WebhookPaymentRequest;
import org.gcrel.tfbackevent.payment.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.payment.exception.PaymentNotificationPendingException;
import org.gcrel.tfbackevent.payment.exception.WebhookValidationException;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {
    private final PaymentRepository paymentRepository;
    private final MercadoPagoService mercadoPagoService;
    private final OrganizerMpAccountRepository mpAccountRepository;
    private final MpOAuthService mpOAuthService;
    private final PaymentService paymentService;

    private static final long MAX_SIGNATURE_AGE_SECONDS = 300;
    private final String webhookSecret;

    public WebhookService(PaymentRepository paymentRepository, MercadoPagoService mercadoPagoService,
            OrganizerMpAccountRepository mpAccountRepository, MpOAuthService mpOAuthService,
            PaymentService paymentService, @Value("${mercadopago.webhook-secret}") String webhookSecret) {
        this.paymentRepository = paymentRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.mpAccountRepository = mpAccountRepository;
        this.mpOAuthService = mpOAuthService;
        this.paymentService = paymentService;
        this.webhookSecret = webhookSecret;
    }

    public void processPaymentNotification(
            WebhookPaymentRequest request, String xSignature, String xRequestId, String dataId) {

        validateSignature(xSignature, xRequestId, dataId, request.data().id());

        if (!"payment".equals(request.type())) {
            return;
        }

        Payment payment = paymentRepository
                .findByTransactionId(dataId)
                .orElse(null);

        if (payment == null) {
            throw new PaymentNotificationPendingException();
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            return;
        }

        String organizerAccessToken = getOrganizerToken(payment);

        com.mercadopago.resources.payment.Payment mpPayment = mercadoPagoService
                .getPaymentById(dataId, organizerAccessToken);

        try {
            paymentService.handleExistingPayment(payment, mpPayment);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Otro proceso ganó la carrera de reconciliación; el estado ya fue actualizado.
        }
    }

    private String getOrganizerToken(Payment payment) {
        OrganizerMpAccount account = mpAccountRepository
                .findByPaymentTransactionId(payment.getTransactionId(), EventRole.ORGANIZER)
                .orElseThrow(MpAccountNotLinkedException::new);

        return mpOAuthService.getValidAccessToken(account);
    }

    private void validateSignature(
            String xSignature, String xRequestId, String dataId, String payloadDataId) {
        try {
            Map<String, String> signatureParts = extractSignatureData(xSignature);
            String ts = signatureParts.get("ts");
            String receivedSignature = signatureParts.get("v1");

            if (ts == null || receivedSignature == null
                    || dataId == null || !dataId.equals(payloadDataId)
                    || isTimestampTooOld(ts)) {
                throw new WebhookValidationException();
            }

            String message = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts + ";";

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hash);

            if (!MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8))) {
                throw new WebhookValidationException();
            }
        } catch (NumberFormatException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new WebhookValidationException();
        }
    }

    private boolean isTimestampTooOld(String timestamp) {
        long rawTimestamp = Long.parseLong(timestamp);
        long timestampSeconds = rawTimestamp > 100_000_000_000L
                ? rawTimestamp / 1000
                : rawTimestamp;
        long age = Instant.now().getEpochSecond() - timestampSeconds;
        return Math.abs(age) > MAX_SIGNATURE_AGE_SECONDS;
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
