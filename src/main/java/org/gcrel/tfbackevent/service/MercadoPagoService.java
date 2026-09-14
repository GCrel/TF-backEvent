package org.gcrel.tfbackevent.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;

import jakarta.annotation.PostConstruct;

import org.gcrel.tfbackevent.exception.PaymentRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MercadoPagoService {
    @Value("${MERCADO_PAGO_ACCESS_TOKEN}")
    private String accessToken;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    public Payment processPayment(
            String cardToken,
            BigDecimal amount,
            Integer installments,
            String payerEmail,
            String paymentMethodId) {
        try {
            PaymentClient client = new PaymentClient();

            PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
                    .transactionAmount(amount)
                    .token(cardToken)
                    .installments(installments)
                    .paymentMethodId(paymentMethodId)
                    .payer(PaymentPayerRequest.builder().email(payerEmail).build())
                    .build();

            return client.create(paymentRequest);

        } catch (MPException | MPApiException e) {
            throw new PaymentRejectedException(e.getMessage());
        }
    }

    public com.mercadopago.resources.payment.Payment getPaymentById(String paymentId) {
        try {
            PaymentClient client = new PaymentClient();
            return client.get(Long.parseLong(paymentId));
        } catch (MPException | MPApiException e) {
            throw new PaymentRejectedException(e.getMessage());
        }
    }
}
