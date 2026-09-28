package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.service.PaymentService;
import org.gcrel.tfbackevent.payment.service.QRService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mercadopago.resources.payment.Payment;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private QRService qrService;

    @InjectMocks
    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<org.gcrel.tfbackevent.model.Payment> paymentCaptor;

    private User attendee;
    private Ticket ticket;
    private Payment mpPaymentMock;
    private org.gcrel.tfbackevent.model.Payment localPayment;

    @BeforeEach
    void setUp() {
        attendee = new User();
        attendee.setEmail("test@payment.com");

        Event event = new Event("Test Event", "Event Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("100.00")) {
        };

        ticket = new Ticket(LocalDateTime.now(), event, attendee);
        ticket.setId(UUID.randomUUID());

        mpPaymentMock = mock(Payment.class);

        localPayment = new org.gcrel.tfbackevent.model.Payment(
                "123456789",
                new BigDecimal("1500.00"),
                LocalDateTime.now(),
                ticket);
    }

    @Test
    void handleNewPayment_WhenApproved_SavesActiveTicketAndReturnsResponse() {
        when(mpPaymentMock.getStatus()).thenReturn("approved");
        when(mpPaymentMock.getId()).thenReturn(123456789L);
        when(mpPaymentMock.getTransactionAmount()).thenReturn(new BigDecimal("1500.00"));
        when(mpPaymentMock.getStatusDetail()).thenReturn("accredited");

        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
        when(qrService.generateQRBase64(ticket.getId())).thenReturn("qr_base64_string");

        PurchaseResponse response = paymentService.handleNewPayment(ticket, mpPaymentMock, attendee);

        assertEquals("approved", response.getStatus());
        assertEquals("accredited", response.getStatusDetail());
        assertEquals("qr_base64_string", response.getQrCodeBase64());
        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());

        verify(paymentRepository).save(paymentCaptor.capture());
        org.gcrel.tfbackevent.model.Payment savedPayment = paymentCaptor.getValue();

        assertEquals(PaymentStatus.APPROVED, savedPayment.getPaymentStatus());
        assertEquals(DeliveryStatus.PENDING, savedPayment.getDeliveryStatus());
    }

    @Test
    void handleNewPayment_WhenApprovedButQRFails_IgnoresExceptionAndReturnsNullQR() {
        when(mpPaymentMock.getStatus()).thenReturn("approved");
        when(mpPaymentMock.getId()).thenReturn(123456789L);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        when(qrService.generateQRBase64(ticket.getId())).thenThrow(new QRGenerationException());

        PurchaseResponse response = paymentService.handleNewPayment(ticket, mpPaymentMock, attendee);

        assertEquals("approved", response.getStatus());
        assertNull(response.getQrCodeBase64());
        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
        verify(paymentRepository).save(any());
    }

    @Test
    void handleNewPayment_WhenRejected_SavesCancelledTicketAndDoesNotCallQR() {
        when(mpPaymentMock.getStatus()).thenReturn("rejected");
        when(mpPaymentMock.getId()).thenReturn(123456789L);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        PurchaseResponse response = paymentService.handleNewPayment(ticket, mpPaymentMock, attendee);

        assertEquals("rejected", response.getStatus());
        assertNull(response.getQrCodeBase64());
        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());

        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.REJECTED, paymentCaptor.getValue().getPaymentStatus());
        verify(qrService, never()).generateQRBase64(any());
    }

    @Test
    void handleExistingPayment_WhenPaymentNotPending_ReturnsEarly() {
        localPayment.setPaymentStatus(PaymentStatus.APPROVED);
        when(paymentRepository.findByTransactionId(localPayment.getTransactionId()))
                .thenReturn(Optional.of(localPayment));

        paymentService.handleExistingPayment(localPayment, mpPaymentMock);

        verify(paymentRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void handleExistingPayment_WhenApproved_ApprovesLocalEntities() {
        localPayment.setPaymentStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByTransactionId(localPayment.getTransactionId()))
                .thenReturn(Optional.of(localPayment));
        when(mpPaymentMock.getStatus()).thenReturn("approved");

        paymentService.handleExistingPayment(localPayment, mpPaymentMock);

        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
        assertEquals(PaymentStatus.APPROVED, localPayment.getPaymentStatus());
        assertEquals(DeliveryStatus.PENDING, localPayment.getDeliveryStatus());

        verify(ticketRepository).save(ticket);
        verify(paymentRepository).save(localPayment);
    }

    @Test
    void handleExistingPayment_WhenStillPending_UpdatesStatusDetailOnly() {
        localPayment.setPaymentStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByTransactionId(localPayment.getTransactionId()))
                .thenReturn(Optional.of(localPayment));
        when(mpPaymentMock.getStatus()).thenReturn("in_process");
        when(mpPaymentMock.getStatusDetail()).thenReturn("pending_review_manual");

        paymentService.handleExistingPayment(localPayment, mpPaymentMock);

        assertEquals(PaymentStatus.PENDING, localPayment.getPaymentStatus());
        assertEquals("in_process", localPayment.getMercadoPagoStatus());
        assertEquals("pending_review_manual", localPayment.getStatusDetail());

        verify(paymentRepository).save(localPayment);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void requestDelivery_WhenPaymentApprovedAndNotSent_SetsDeliveryToPending() {
        localPayment.setPaymentStatus(PaymentStatus.APPROVED);
        localPayment.setDeliveryStatus(DeliveryStatus.FAILED);
        when(paymentRepository.findByTicket(ticket)).thenReturn(Optional.of(localPayment));

        paymentService.requestDelivery(ticket);

        assertEquals(DeliveryStatus.PENDING, localPayment.getDeliveryStatus());
        assertNotNull(localPayment.getNextDeliveryAttemptAt());
        verify(paymentRepository).save(localPayment);
    }

    @Test
    void requestDelivery_WhenPaymentIsAlreadySent_DoesNothing() {
        localPayment.setPaymentStatus(PaymentStatus.APPROVED);
        localPayment.setDeliveryStatus(DeliveryStatus.SENT);
        when(paymentRepository.findByTicket(ticket)).thenReturn(Optional.of(localPayment));

        paymentService.requestDelivery(ticket);

        assertEquals(DeliveryStatus.SENT, localPayment.getDeliveryStatus());
        verify(paymentRepository, never()).save(any());
    }
}
