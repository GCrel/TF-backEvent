package org.gcrel.tfbackevent.service;

import org.gcrel.tfbackevent.dto.request.WebhookPaymentRequest;
import org.gcrel.tfbackevent.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.exception.WebhookValidationException;
import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class WebhookService {
    private final PaymentRepository paymentRepository;
    private final MercadoPagoService mercadoPagoService;
    private final OrganizerMpAccountRepository mpAccountRepository;
    private final MpOAuthService mpOAuthService;
    private final PaymentService paymentService;

    @Value("${MERCADO_PAGO_WEBHOOK_SECRET}")
    private String webhookSecret;

    public WebhookService(PaymentRepository paymentRepository, MercadoPagoService mercadoPagoService,
            OrganizerMpAccountRepository mpAccountRepository, MpOAuthService mpOAuthService,
            PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.mpAccountRepository = mpAccountRepository;
        this.mpOAuthService = mpOAuthService;
        this.paymentService = paymentService;
    }

    public void processPaymentNotification(WebhookPaymentRequest request, String xSignature, String xRequestId) {

        validateSignature(xSignature, xRequestId, request.getData().getId());

        if (!"payment".equals(request.getType())) {
            return;
        }

        Payment payment = paymentRepository
                .findByTransactionId(request.getData().getId())
                .orElse(null);

        if (payment == null || payment.getPaymentStatus() != PaymentStatus.PENDING) {
            return;
        }

        String organizerAccessToken = getOrganizerToken(payment);

        com.mercadopago.resources.payment.Payment mpPayment = mercadoPagoService
                .getPaymentById(request.getData().getId(), organizerAccessToken);

        paymentService.handleExistingPayment(payment, mpPayment);
    }

    private String getOrganizerToken(Payment payment) {
        OrganizerMpAccount account = mpAccountRepository
                .findByPaymentTransactionId(payment.getTransactionId(), EventRole.ORGANIZER)
                .orElseThrow(MpAccountNotLinkedException::new);

        return mpOAuthService.getValidAccessToken(account);
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
