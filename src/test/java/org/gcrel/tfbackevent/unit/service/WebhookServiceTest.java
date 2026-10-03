package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.request.WebhookPaymentRequest;
import org.gcrel.tfbackevent.payment.exception.PaymentNotificationPendingException;
import org.gcrel.tfbackevent.payment.exception.WebhookValidationException;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.service.MercadoPagoService;
import org.gcrel.tfbackevent.payment.service.MpOAuthService;
import org.gcrel.tfbackevent.payment.service.PaymentService;
import org.gcrel.tfbackevent.payment.service.QRService;
import org.gcrel.tfbackevent.payment.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mercadopago.resources.payment.Payment;

@ExtendWith(MockitoExtension.class)
public class WebhookServiceTest {
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrganizerMpAccountRepository mpAccountRepository;
    @Mock
    private MpOAuthService mpOAuthService;
    @Mock
    private MercadoPagoService mercadoPagoService;
    @Mock
    private QRService qrService;
    @Mock
    private EmailService emailService;
    @Mock
    private PaymentService paymentService;

    private WebhookService webhookService;

    private static final String SECRET = "test-secret-key";
    private static final String REQUEST_ID = "req-12345";
    private static final String PAYMENT_ID = "987654321";
    private static final String ORGANIZER_TOKEN = "ORGANIZER-TOKEN-123";

    private WebhookPaymentRequest request;
    private Payment mpPaymentMock;
    private org.gcrel.tfbackevent.model.Payment localPayment;
    private Ticket ticket;
    private User attendee;
    private OrganizerMpAccount organizerAccount;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
                paymentRepository,
                mercadoPagoService,
                mpAccountRepository,
                mpOAuthService,
                paymentService,
                SECRET);

        // 1. Usando tu @AllArgsConstructor y @Data reales
        WebhookPaymentRequest.Data requestData = new WebhookPaymentRequest.Data(PAYMENT_ID);
        request = new WebhookPaymentRequest("payment.created", "payment", requestData);

        attendee = new User();
        attendee.setEmail("test@webhook.com");
        Event event = new Event(
                "Test Event",
                "Description",
                LocalDateTime.now().plusDays(1),
                new BigDecimal("1000.00")) {
            @Override
            public org.gcrel.tfbackevent.model.enums.EventType getType() {
                return org.gcrel.tfbackevent.model.enums.EventType.IN_PERSON;
            }
        };

        User organizer = new User();
        organizer.setEmail("organizer@webhook.com");

        organizerAccount = new OrganizerMpAccount(
                organizer,
                ORGANIZER_TOKEN,
                "refresh-token",
                "12345",
                "organizer@webhook.com",
                LocalDateTime.now().plusDays(1));

        ticket = new Ticket(LocalDateTime.now(), event, attendee);
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(TicketStatus.ACTIVE);

        localPayment = new org.gcrel.tfbackevent.model.Payment(
                PAYMENT_ID, new BigDecimal("1000.00"), LocalDateTime.now(), ticket);

        mpPaymentMock = mock(Payment.class);
    }

    private String generateValidSignature(String paymentId, String requestId, String ts)
            throws Exception {
        String message = "id:" + paymentId + ";request-id:" + requestId + ";ts:" + ts + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        String hexHash = HexFormat.of().formatHex(hash);
        return "ts=" + ts + ",v1=" + hexHash;
    }

    private String currentTimestamp() {
        return Long.toString(Instant.now().getEpochSecond());
    }

    @Test
    void processPaymentNotification_WhenSignatureIsInvalid_ThrowsException() {
        String invalidSignature = "ts=" + currentTimestamp() + ",v1=badhash123";

        assertThrows(
                WebhookValidationException.class,
                () -> webhookService.processPaymentNotification(
                        request, invalidSignature, REQUEST_ID, PAYMENT_ID));

        verify(mercadoPagoService, never()).getPaymentById(anyString(), anyString());
    }

    @Test
    void processPaymentNotification_WhenSignatureIsTooOld_ThrowsException() throws Exception {
        String timestamp = Long.toString(Instant.now().minusSeconds(301).getEpochSecond());
        String signature = generateValidSignature(PAYMENT_ID, REQUEST_ID, timestamp);

        assertThrows(
                WebhookValidationException.class,
                () -> webhookService.processPaymentNotification(
                        request, signature, REQUEST_ID, PAYMENT_ID));

        verify(paymentRepository, never()).findByTransactionId(anyString());
    }

    @Test
    void processPaymentNotification_WhenDataIdIsMissing_ThrowsException() throws Exception {
        String timestamp = currentTimestamp();
        String signature = generateValidSignature(PAYMENT_ID, REQUEST_ID, timestamp);

        assertThrows(
                WebhookValidationException.class,
                () -> webhookService.processPaymentNotification(
                        request, signature, REQUEST_ID, null));

        verify(paymentRepository, never()).findByTransactionId(anyString());
    }

    @Test
    void processPaymentNotification_WhenDataIdDoesNotMatchPayload_ThrowsException() throws Exception {
        String timestamp = currentTimestamp();
        String signature = generateValidSignature("111111111", REQUEST_ID, timestamp);

        assertThrows(
                WebhookValidationException.class,
                () -> webhookService.processPaymentNotification(
                        request, signature, REQUEST_ID, "111111111"));

        verify(paymentRepository, never()).findByTransactionId(anyString());
    }

    @Test
    void processPaymentNotification_WhenTimestampIsInMilliseconds_AcceptsSignature() throws Exception {
        String timestamp = Long.toString(Instant.now().toEpochMilli());
        String signature = generateValidSignature(PAYMENT_ID, REQUEST_ID, timestamp);

        when(paymentRepository.findByTransactionId(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThrows(
                PaymentNotificationPendingException.class,
                () -> webhookService.processPaymentNotification(
                        request, signature, REQUEST_ID, PAYMENT_ID));
    }

    @Test
    void processPaymentNotification_WhenTypeIsNotPayment_ReturnsEarly() throws Exception {
        WebhookPaymentRequest.Data requestData = new WebhookPaymentRequest.Data(PAYMENT_ID);
        request = new WebhookPaymentRequest("payment.created", "plan", requestData);

        String timestamp = currentTimestamp();
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, timestamp);

        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID, PAYMENT_ID);

        verify(mercadoPagoService, never()).getPaymentById(anyString(), anyString());
    }

    @Test
    void processPaymentNotification_WhenPaymentIsApproved_UpdatesAndSendsEmail() throws Exception {
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, currentTimestamp());

        when(mpAccountRepository.findByPaymentTransactionId(PAYMENT_ID, EventRole.ORGANIZER))
                .thenReturn(Optional.of(organizerAccount));
        when(mpOAuthService.getValidAccessToken(organizerAccount)).thenReturn(ORGANIZER_TOKEN);

        when(mercadoPagoService.getPaymentById(PAYMENT_ID, ORGANIZER_TOKEN))
                .thenReturn(mpPaymentMock);
        when(paymentRepository.findByTransactionId(PAYMENT_ID))
                .thenReturn(Optional.of(localPayment));
        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID, PAYMENT_ID);

        verify(paymentService).handleExistingPayment(localPayment, mpPaymentMock);
    }

    @Test
    void processPaymentNotification_WhenPaymentIsRejected_UpdatesStatusOnly() throws Exception {
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, currentTimestamp());

        when(mpAccountRepository.findByPaymentTransactionId(PAYMENT_ID, EventRole.ORGANIZER))
                .thenReturn(Optional.of(organizerAccount));
        when(mpOAuthService.getValidAccessToken(organizerAccount)).thenReturn(ORGANIZER_TOKEN);

        when(mercadoPagoService.getPaymentById(PAYMENT_ID, ORGANIZER_TOKEN))
                .thenReturn(mpPaymentMock);
        when(paymentRepository.findByTransactionId(PAYMENT_ID))
                .thenReturn(Optional.of(localPayment));

        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID, PAYMENT_ID);

        verify(paymentService).handleExistingPayment(localPayment, mpPaymentMock);
    }

    @Test
    void processPaymentNotification_WhenLocalPaymentNotFound_RequestsRetry() throws Exception {
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, currentTimestamp());

        when(paymentRepository.findByTransactionId(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThrows(PaymentNotificationPendingException.class,
                () -> webhookService.processPaymentNotification(
                        request, validSignature, REQUEST_ID, PAYMENT_ID));

        verify(mercadoPagoService, never()).getPaymentById(anyString(), anyString());
        verify(ticketRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPaymentNotification_WhenPaymentIsNotPending_ReturnsEarly() throws Exception {
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, currentTimestamp());

        localPayment.setPaymentStatus(PaymentStatus.APPROVED);

        when(paymentRepository.findByTransactionId(PAYMENT_ID))
                .thenReturn(Optional.of(localPayment));

        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID, PAYMENT_ID);

        verify(mercadoPagoService, never()).getPaymentById(anyString(), anyString());
        verify(mpAccountRepository, never()).findByPaymentTransactionId(anyString(), any());
        verify(ticketRepository, never()).save(any());
        verify(mpPaymentMock, never()).getStatus();
    }
}
