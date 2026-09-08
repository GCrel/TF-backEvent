package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mercadopago.resources.payment.Payment;

import org.gcrel.tfbackevent.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.exception.*;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.repository.EventRepository;
import org.gcrel.tfbackevent.repository.PaymentRepository;
import org.gcrel.tfbackevent.repository.TicketRepository;
import org.gcrel.tfbackevent.service.EmailService;
import org.gcrel.tfbackevent.service.MercadoPagoService;
import org.gcrel.tfbackevent.service.QRService;
import org.gcrel.tfbackevent.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class TicketServiceTest {
    @Mock private TicketRepository ticketRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private EventRepository eventRepository;
    @Mock private MercadoPagoService mercadoPagoService;
    @Mock private QRService qrService;
    @Mock private EmailService emailService;

    @InjectMocks private TicketService ticketService;

    private User attendee;
    private Event event;
    private PurchaseTicketRequest purchaseRequest;
    private Payment mpPaymentMock;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        attendee = new User();
        attendee.setId(UUID.randomUUID());
        attendee.setEmail("test@test.com");

        event =
                new Event(
                        "Test Event",
                        "Description",
                        LocalDateTime.now().plusDays(1),
                        new BigDecimal("1000.00")) {};
        event.setId(UUID.randomUUID());
        event.setStatus(EventStatus.PUBLISHED);

        purchaseRequest =
                new PurchaseTicketRequest(
                        UUID.randomUUID(), "token123", 1, "test@test.com", "visa");

        mpPaymentMock = mock(Payment.class);

        ticket = new Ticket(LocalDateTime.now(), event, attendee);
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(TicketStatus.ACTIVE);
    }

    @Test
    void purchase_WhenPaymentApproved_ReturnsApprovedResponse() {
        when(eventRepository.findById(purchaseRequest.getEventId())).thenReturn(Optional.of(event));
        when(mpPaymentMock.getStatus()).thenReturn("approved");
        when(mpPaymentMock.getId()).thenReturn(123456789L);
        when(mpPaymentMock.getTransactionAmount()).thenReturn(new BigDecimal("1000.00"));

        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any()))
                .thenReturn(mpPaymentMock);
        when(qrService.generateQRBase64(any())).thenReturn("qr_base64_string");

        PurchaseResponse response = ticketService.purchase(purchaseRequest, attendee);

        assertEquals("approved", response.getStatus());
        assertEquals("qr_base64_string", response.getQrCodeBase64());

        verify(ticketRepository, times(2)).save(any(Ticket.class));
        verify(paymentRepository, times(1)).save(any());
        verify(emailService, times(1))
                .sendTicketEmail(
                        eq(attendee.getEmail()), any(Ticket.class), eq("qr_base64_string"));
    }

    @Test
    void purchase_WhenEventNotPublished_ThrowsException() {
        event.setStatus(EventStatus.DRAFT);
        when(eventRepository.findById(purchaseRequest.getEventId())).thenReturn(Optional.of(event));

        assertThrows(
                EventNotAvailableException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void purchase_WhenPaymentRejectedExceptionThrown_CancelsTicketAndThrows() {
        when(eventRepository.findById(purchaseRequest.getEventId())).thenReturn(Optional.of(event));
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any()))
                .thenThrow(new PaymentRejectedException("Rejected"));

        assertThrows(
                PaymentRejectedException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(ticketRepository, times(2))
                .save(
                        argThat(
                                t ->
                                        t.getStatus() == TicketStatus.CANCELLED
                                                || t.getStatus() == null));
    }

    @Test
    void resendTicketEmail_WhenValid_SendsEmail() {
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(qrService.generateQRBase64(ticket.getId())).thenReturn("qr_base64");

        ticketService.resendTicketEmail(ticket.getId(), attendee);

        verify(emailService, times(1)).sendTicketEmail(attendee.getEmail(), ticket, "qr_base64");
    }

    @Test
    void resendTicketEmail_WhenUnauthorizedUser_ThrowsException() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(
                UnauthorizedTicketAccessException.class,
                () -> ticketService.resendTicketEmail(ticket.getId(), otherUser));

        verify(emailService, never()).sendTicketEmail(anyString(), any(), anyString());
    }

    @Test
    void resendTicketEmail_WhenTicketNotActive_ThrowsException() {
        ticket.setStatus(TicketStatus.CANCELLED);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(
                TicketNotActiveException.class,
                () -> ticketService.resendTicketEmail(ticket.getId(), attendee));
    }
}
