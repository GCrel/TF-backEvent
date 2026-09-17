package org.gcrel.tfbackevent.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;

import org.gcrel.tfbackevent.exception.PaymentRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MercadoPagoService {
    @Value("${mercadopago.client-id}")
    private String clientId;

    public com.mercadopago.resources.payment.Payment processPayment(String cardToken, BigDecimal amount,
            Integer installments, String payerEmail, String paymentMethodId, String organizerAccessToken) {
        try {
            MPRequestOptions requestOptions = MPRequestOptions.builder()
                    .accessToken(organizerAccessToken)
                    .build();

            PaymentClient client = new PaymentClient();

            PaymentCreateRequest paymentRequest = PaymentCreateRequest.builder()
                    .transactionAmount(amount)
                    .token(cardToken)
                    .installments(installments)
                    .paymentMethodId(paymentMethodId)
                    .applicationFee(BigDecimal.ZERO) // Assuming no application fee for the organizer
                    .payer(PaymentPayerRequest.builder()
                            .email(payerEmail)
                            .build())
                    .build();

            return client.create(paymentRequest, requestOptions);

        } catch (MPException | MPApiException e) {
            throw new PaymentRejectedException(e.getMessage());
        }
    }

    public com.mercadopago.resources.payment.Payment getPaymentById(String paymentId, String organizerAccessToken) {
        try {
            MercadoPagoConfig.setAccessToken(organizerAccessToken);
            PaymentClient client = new PaymentClient();
            return client.get(Long.parseLong(paymentId));
        } catch (MPException | MPApiException e) {
            throw new PaymentRejectedException(e.getMessage());
        }
    }
}
