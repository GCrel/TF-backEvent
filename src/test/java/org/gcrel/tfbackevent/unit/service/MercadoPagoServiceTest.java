package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.gcrel.tfbackevent.payment.exception.PaymentProcessingException;
import org.gcrel.tfbackevent.payment.service.MercadoPagoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;

@ExtendWith(MockitoExtension.class)
public class MercadoPagoServiceTest {
    @InjectMocks
    private MercadoPagoService mercadoPagoService;

    private static final String ORGANIZER_TOKEN = "ORGANIZER-TOKEN-123";

    @Test
    void processPayment_WhenSuccessful_UsesOrganizerTokenAndReturnsPayment() throws Exception {
        Payment expectedPayment = mock(Payment.class);

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(
                PaymentClient.class,
                (mock, context) -> {
                    when(mock.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class)))
                            .thenReturn(expectedPayment);
                })) {

            Payment result = mercadoPagoService.processPayment(
                    "card_token",
                    new BigDecimal("1500.00"),
                    1,
                    "payer@email.com",
                    "visa",
                    ORGANIZER_TOKEN,
                    "ticket-id");

            assertNotNull(result);
            assertEquals(expectedPayment, result);

            assertEquals(1, mockedClient.constructed().size());
            verify(mockedClient.constructed().get(0), times(1))
                    .create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
        }
    }

    @Test
    void processPayment_WhenMPExceptionThrown_ThrowsPaymentProcessingException() throws Exception {
        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(
                PaymentClient.class,
                (mock, context) -> {
                    when(mock.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class)))
                            .thenThrow(new MPException("MP Error"));
                })) {

            PaymentProcessingException exception = assertThrows(
                    PaymentProcessingException.class,
                    () -> mercadoPagoService.processPayment(
                            "card_token",
                            new BigDecimal("1500.00"),
                            1,
                            "payer@email.com",
                            "visa",
                            ORGANIZER_TOKEN,
                            "ticket-id"));

            assertTrue(exception.getMessage().contains("MP Error"));
        }
    }

    @Test
    void getPaymentById_WhenSuccessful_ReturnsPayment() throws Exception {
        Payment expectedPayment = mock(Payment.class);
        String paymentId = "123456789";

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(
                PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(anyLong(), any(MPRequestOptions.class))).thenReturn(expectedPayment);
                })) {

            Payment result = mercadoPagoService.getPaymentById(paymentId, ORGANIZER_TOKEN);

            assertNotNull(result);
            assertEquals(expectedPayment, result);

            verify(mockedClient.constructed().get(0), times(1))
                    .get(eq(123456789L), any(MPRequestOptions.class));
        }
    }

    @Test
    void getPaymentById_UsesOrganizerAccessTokenInRequestOptions() throws Exception {
        Payment expectedPayment = mock(Payment.class);
        String paymentId = "123456789";

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(
                PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(anyLong(), any(MPRequestOptions.class))).thenReturn(expectedPayment);
                })) {

            mercadoPagoService.getPaymentById(paymentId, ORGANIZER_TOKEN);

            verify(mockedClient.constructed().get(0)).get(eq(123456789L), any(MPRequestOptions.class));
        }
    }

    @Test
    void getPaymentById_WhenMPApiExceptionThrown_ThrowsPaymentProcessingException() throws Exception {
        String paymentId = "123456789";

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(
                PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(anyLong(), any(MPRequestOptions.class))).thenThrow(new MPException("API Error"));
                })) {

            PaymentProcessingException exception = assertThrows(
                    PaymentProcessingException.class,
                    () -> mercadoPagoService.getPaymentById(paymentId, ORGANIZER_TOKEN));

            assertTrue(exception.getMessage().contains("API Error"));
        }
    }
}
