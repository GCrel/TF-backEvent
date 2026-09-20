package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;

import org.gcrel.tfbackevent.exception.PaymentRejectedException;
import org.gcrel.tfbackevent.service.MercadoPagoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

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
                    ORGANIZER_TOKEN);

            assertNotNull(result);
            assertEquals(expectedPayment, result);

            assertEquals(1, mockedClient.constructed().size());
            verify(mockedClient.constructed().get(0), times(1))
                    .create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
        }
    }

    @Test
    void processPayment_WhenMPExceptionThrown_ThrowsPaymentRejectedException() throws Exception {
        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(
                PaymentClient.class,
                (mock, context) -> {
                    when(mock.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class)))
                            .thenThrow(new MPException("MP Error"));
                })) {

            PaymentRejectedException exception = assertThrows(
                    PaymentRejectedException.class,
                    () -> mercadoPagoService.processPayment(
                            "card_token",
                            new BigDecimal("1500.00"),
                            1,
                            "payer@email.com",
                            "visa",
                            ORGANIZER_TOKEN));

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
                    when(mock.get(anyLong())).thenReturn(expectedPayment);
                })) {

            Payment result = mercadoPagoService.getPaymentById(paymentId, ORGANIZER_TOKEN);

            assertNotNull(result);
            assertEquals(expectedPayment, result);

            verify(mockedClient.constructed().get(0), times(1)).get(123456789L);
        }
    }

    @Test
    void getPaymentById_SetsOrganizerAccessTokenInMercadoPagoConfig() throws Exception {
        Payment expectedPayment = mock(Payment.class);
        String paymentId = "123456789";

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(
                PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(anyLong())).thenReturn(expectedPayment);
                })) {

            mercadoPagoService.getPaymentById(paymentId, ORGANIZER_TOKEN);

            assertEquals(ORGANIZER_TOKEN, MercadoPagoConfig.getAccessToken());
        }
    }

    @Test
    void getPaymentById_WhenMPApiExceptionThrown_ThrowsPaymentRejectedException() throws Exception {
        String paymentId = "123456789";

        try (MockedConstruction<PaymentClient> mockedClient = mockConstruction(
                PaymentClient.class,
                (mock, context) -> {
                    when(mock.get(anyLong())).thenThrow(new MPException("API Error"));
                })) {

            PaymentRejectedException exception = assertThrows(
                    PaymentRejectedException.class,
                    () -> mercadoPagoService.getPaymentById(paymentId, ORGANIZER_TOKEN));

            assertTrue(exception.getMessage().contains("API Error"));
        }
    }
}
