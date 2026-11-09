package org.gcrel.tfbackevent.payment.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.gcrel.tfbackevent.payment.exception.PaymentProcessingException;
import org.gcrel.tfbackevent.payment.exception.PaymentRejectedException;
import org.springframework.stereotype.Service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPSearchRequest;

@Service
public class MercadoPagoService {
    public com.mercadopago.resources.payment.Payment processPayment(String cardToken, BigDecimal amount,
            Integer installments, String payerEmail, String paymentMethodId, String organizerAccessToken,
            String idempotencyKey) {
        try {
            MPRequestOptions requestOptions = MPRequestOptions.builder()
                    .accessToken(organizerAccessToken)
                    .customHeaders(Map.of("X-Idempotency-Key", idempotencyKey))
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
                    .externalReference(idempotencyKey)
                    .build();

            return client.create(paymentRequest, requestOptions);

        } catch (MPApiException e) {
            if (e.getStatusCode() == 400 || e.getStatusCode() == 422) {
                throw new PaymentRejectedException(e.getMessage());
            }
            throw processingException(e);
        } catch (MPException e) {
            throw new PaymentProcessingException(e.getMessage(), e, true);
        }
    }

    public com.mercadopago.resources.payment.Payment getPaymentById(String paymentId, String organizerAccessToken) {
        try {
            PaymentClient client = new PaymentClient();
            MPRequestOptions options = MPRequestOptions.builder()
                    .accessToken(organizerAccessToken)
                    .build();
            return client.get(Long.parseLong(paymentId), options);
        } catch (MPApiException e) {
            throw processingException(e);
        } catch (MPException e) {
            throw new PaymentProcessingException(e.getMessage(), e, true);
        }
    }

    public Optional<com.mercadopago.resources.payment.Payment> findPaymentByExternalReference(
            String externalReference, String organizerAccessToken) {
        try {
            MPSearchRequest request = MPSearchRequest.builder()
                    .limit(1)
                    .filters(Map.of("external_reference", externalReference))
                    .build();
            MPRequestOptions options = MPRequestOptions.builder()
                    .accessToken(organizerAccessToken)
                    .build();
            var results = new PaymentClient().searchAll(request, options);
            var iterator = results.iterator();
            return iterator.hasNext()
                    ? Optional.of(iterator.next())
                    : Optional.empty();
        } catch (MPApiException e) {
            throw processingException(e);
        } catch (MPException e) {
            throw new PaymentProcessingException(e.getMessage(), e, true);
        }
    }

    private PaymentProcessingException processingException(MPApiException exception) {
        int status = exception.getStatusCode();
        boolean outcomeUnknown = status >= 500;
        return new PaymentProcessingException(exception.getMessage(), exception, outcomeUnknown);
    }
}
