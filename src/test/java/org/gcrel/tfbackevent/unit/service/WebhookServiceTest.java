package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.mercadopago.resources.payment.Payment;

import org.gcrel.tfbackevent.dto.request.WebhookPaymentRequest;
import org.gcrel.tfbackevent.exception.WebhookValidationException;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.repository.PaymentRepository;
import org.gcrel.tfbackevent.repository.TicketRepository;
import org.gcrel.tfbackevent.service.EmailService;
import org.gcrel.tfbackevent.service.MercadoPagoService;
import org.gcrel.tfbackevent.service.MpOAuthService;
import org.gcrel.tfbackevent.service.QRService;
import org.gcrel.tfbackevent.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

    @InjectMocks
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
        ReflectionTestUtils.setField(webhookService, "webhookSecret", SECRET);

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

    @Test
    void processPaymentNotification_WhenSignatureIsInvalid_ThrowsException() {
        String invalidSignature = "ts=123456,v1=badhash123";

        assertThrows(
                WebhookValidationException.class,
                () -> webhookService.processPaymentNotification(
                        request, invalidSignature, REQUEST_ID));

        verify(mercadoPagoService, never()).getPaymentById(anyString(), anyString());
    }

    @Test
    void processPaymentNotification_WhenTypeIsNotPayment_ReturnsEarly() throws Exception {
        request.setType("plan"); // Modificamos gracias a @Data
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, "123456789");

        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID);

        verify(mercadoPagoService, never()).getPaymentById(anyString(), anyString());
    }

    @Test
    void processPaymentNotification_WhenPaymentIsApproved_UpdatesAndSendsEmail() throws Exception {
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, "123456789");

        when(mpAccountRepository.findByPaymentTransactionId(PAYMENT_ID, EventRole.ORGANIZER))
                .thenReturn(Optional.of(organizerAccount));
        when(mpOAuthService.getValidAccessToken(organizerAccount)).thenReturn(ORGANIZER_TOKEN);

        when(mercadoPagoService.getPaymentById(PAYMENT_ID, ORGANIZER_TOKEN))
                .thenReturn(mpPaymentMock);
        when(mpPaymentMock.getStatus()).thenReturn("approved");

        when(paymentRepository.findByTransactionId(PAYMENT_ID))
                .thenReturn(Optional.of(localPayment));
        when(qrService.generateQRBase64(ticket.getId())).thenReturn("qr_base64_string");

        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID);

        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
        assertEquals(PaymentStatus.APPROVED, localPayment.getPaymentStatus());
        assertNotNull(localPayment.getPaymentDate());

        verify(ticketRepository).save(ticket);
        verify(paymentRepository).save(localPayment);
        verify(emailService)
                .sendTicketEmail(eq(attendee.getEmail()), eq(ticket), eq("qr_base64_string"));
    }

    @Test
    void processPaymentNotification_WhenPaymentIsRejected_UpdatesStatusOnly() throws Exception {
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, "123456789");

        when(mpAccountRepository.findByPaymentTransactionId(PAYMENT_ID, EventRole.ORGANIZER))
                .thenReturn(Optional.of(organizerAccount));
        when(mpOAuthService.getValidAccessToken(organizerAccount)).thenReturn(ORGANIZER_TOKEN);

        when(mercadoPagoService.getPaymentById(PAYMENT_ID, ORGANIZER_TOKEN))
                .thenReturn(mpPaymentMock);
        when(mpPaymentMock.getStatus()).thenReturn("rejected"); // Podría ser "cancelled" también

        when(paymentRepository.findByTransactionId(PAYMENT_ID))
                .thenReturn(Optional.of(localPayment));

        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID);

        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
        assertEquals(PaymentStatus.REJECTED, localPayment.getPaymentStatus());

        verify(ticketRepository).save(ticket);
        verify(paymentRepository).save(localPayment);
        verify(qrService, never()).generateQRBase64(any());
        verify(emailService, never()).sendTicketEmail(anyString(), any(), anyString());
    }

    @Test
    void processPaymentNotification_WhenLocalPaymentNotFound_ReturnsEarly() throws Exception {
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, "123456789");

        when(paymentRepository.findByTransactionId(PAYMENT_ID)).thenReturn(Optional.empty());

        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID);

        verify(mercadoPagoService, never()).getPaymentById(anyString(), anyString());
        verify(ticketRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPaymentNotification_WhenPaymentIsNotPending_ReturnsEarly() throws Exception {
        String validSignature = generateValidSignature(PAYMENT_ID, REQUEST_ID, "123456789");

        localPayment.setPaymentStatus(PaymentStatus.APPROVED);

        when(paymentRepository.findByTransactionId(PAYMENT_ID))
                .thenReturn(Optional.of(localPayment));

        webhookService.processPaymentNotification(request, validSignature, REQUEST_ID);

        verify(mercadoPagoService, never()).getPaymentById(anyString(), anyString());
        verify(mpAccountRepository, never()).findByPaymentTransactionId(anyString(), any());
        verify(ticketRepository, never()).save(any());
        verify(mpPaymentMock, never()).getStatus();
    }
}
