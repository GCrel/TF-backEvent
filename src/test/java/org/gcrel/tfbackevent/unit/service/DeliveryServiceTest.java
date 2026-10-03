package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.exception.TicketDeliveryException;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.service.DeliveryService;
import org.gcrel.tfbackevent.payment.service.QRService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DeliveryServiceTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private QRService qrService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private DeliveryService deliveryService;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private Payment paymentDue;
    private Payment paymentStale;
    private Ticket ticket;
    private User attendee;
    private Event event;

    @BeforeEach
    void setUp() {
        attendee = new User();
        attendee.setEmail("test@delivery.com");

        event = new Event("Test Event", "Event Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("100.00")) {
            @Override
            public org.gcrel.tfbackevent.model.enums.EventType getType() {
                return org.gcrel.tfbackevent.model.enums.EventType.IN_PERSON;
            }
        };

        ticket = new Ticket(LocalDateTime.now(), event, attendee);
        ticket.setId(UUID.randomUUID());

        paymentDue = new Payment(
                "tx-123",
                new BigDecimal("1000.00"),
                LocalDateTime.now(),
                ticket);
        paymentDue.setDeliveryStatus(DeliveryStatus.PENDING);

        paymentStale = new Payment(
                "tx-456",
                new BigDecimal("1000.00"),
                LocalDateTime.now(),
                ticket);
        paymentStale.setDeliveryStatus(DeliveryStatus.PROCESSING);
    }

    @Test
    void processDueDeliveries_WhenRecoveringStaleDeliveries_UpdatesStatusToFailed() {
        when(paymentRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                eq(DeliveryStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of(paymentStale));

        when(paymentRepository.findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
                any(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        deliveryService.processDueDeliveries();

        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedStale = paymentCaptor.getValue();

        assertEquals(DeliveryStatus.FAILED, savedStale.getDeliveryStatus());
        assertNotNull(savedStale.getNextDeliveryAttemptAt());
    }

    @Test
    void processDueDeliveries_WhenDeliverySuccessful_SetsStatusToSent() {
        when(paymentRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                eq(DeliveryStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        when(paymentRepository.findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
                any(), any(LocalDateTime.class)))
                .thenReturn(List.of(paymentDue));

        when(qrService.generateQRBase64(ticket.getId())).thenReturn("qr_base64");

        deliveryService.processDueDeliveries();

        verify(paymentRepository).saveAndFlush(paymentDue);
        verify(paymentRepository).save(paymentDue);

        assertEquals(DeliveryStatus.SENT, paymentDue.getDeliveryStatus());
        assertEquals(1, paymentDue.getDeliveryAttempts());
        assertNull(paymentDue.getLastDeliveryError());

        verify(emailService).sendTicketEmail(attendee.getEmail(), ticket, "qr_base64");
    }

    @Test
    void processDueDeliveries_WhenVirtualEvent_SendsAccessCredentialsWithoutQr() {
        VirtualEvent virtualEvent = new VirtualEvent(
                "Virtual Event",
                "Online description",
                LocalDateTime.now().plusDays(1),
                new BigDecimal("100.00"),
                "https://meet.example.com/event",
                "Meet",
                "Ingresá cinco minutos antes.");
        ticket = new Ticket(LocalDateTime.now(), virtualEvent, attendee);
        ticket.setId(UUID.randomUUID());
        paymentDue = new Payment(
                "tx-virtual",
                new BigDecimal("1000.00"),
                LocalDateTime.now(),
                ticket);
        paymentDue.setDeliveryStatus(DeliveryStatus.PENDING);

        when(paymentRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                eq(DeliveryStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(paymentRepository.findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
                any(), any(LocalDateTime.class)))
                .thenReturn(List.of(paymentDue));

        deliveryService.processDueDeliveries();

        verify(emailService).sendVirtualTicketEmail(attendee.getEmail(), ticket);
        verify(emailService, never()).sendTicketEmail(any(), any(), any());
        verify(qrService, never()).generateQRBase64(any());
        assertEquals(DeliveryStatus.SENT, paymentDue.getDeliveryStatus());
    }

    @Test
    void processDueDeliveries_WhenQRGenerationFails_SetsStatusToFailedAndSchedulesRetry() {
        when(paymentRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                eq(DeliveryStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        when(paymentRepository.findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
                any(), any(LocalDateTime.class)))
                .thenReturn(List.of(paymentDue));

        when(qrService.generateQRBase64(ticket.getId())).thenThrow(new QRGenerationException());

        deliveryService.processDueDeliveries();

        verify(paymentRepository).save(paymentCaptor.capture());
        Payment failedPayment = paymentCaptor.getValue();

        assertEquals(DeliveryStatus.FAILED, failedPayment.getDeliveryStatus());
        assertEquals(1, failedPayment.getDeliveryAttempts());
        assertEquals("Error al generar el código QR.", failedPayment.getLastDeliveryError());

        verify(emailService, never()).sendTicketEmail(any(), any(), any());
    }

    @Test
    void processDueDeliveries_WhenEmailServiceFails_SetsStatusToFailed() {
        when(paymentRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                eq(DeliveryStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        when(paymentRepository.findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
                any(), any(LocalDateTime.class)))
                .thenReturn(List.of(paymentDue));

        when(qrService.generateQRBase64(ticket.getId())).thenReturn("qr_base64");

        // Simulamos la excepción exacta que nos compartiste (requiere mensaje y causa)
        doThrow(new TicketDeliveryException("Email Error", new RuntimeException("SMTP Timeout")))
                .when(emailService).sendTicketEmail(anyString(), any(), anyString());

        deliveryService.processDueDeliveries();

        verify(paymentRepository).save(paymentCaptor.capture());
        Payment failedPayment = paymentCaptor.getValue();

        assertEquals(DeliveryStatus.FAILED, failedPayment.getDeliveryStatus());
        assertEquals(1, failedPayment.getDeliveryAttempts());
        assertEquals("Email Error", failedPayment.getLastDeliveryError());
    }
}
