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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
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
    private TicketOrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private QRService qrService;

    @InjectMocks
    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<org.gcrel.tfbackevent.model.Payment> paymentCaptor;

    private TicketOrder order;
    private Ticket firstTicket;
    private Ticket secondTicket;
    private Payment mpPaymentMock;
    private org.gcrel.tfbackevent.model.Payment localPayment;

    @BeforeEach
    void setUp() {
        User buyer = new User();
        buyer.setEmail("test@payment.com");

        Event event = new Event("Test Event", "Event Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("100.00")) {
            @Override
            public org.gcrel.tfbackevent.model.enums.EventType getType() {
                return org.gcrel.tfbackevent.model.enums.EventType.IN_PERSON;
            }
        };

        order = new TicketOrder(buyer, event, 2, new BigDecimal("100.00"), LocalDateTime.now());
        order.setId(UUID.randomUUID());
        firstTicket = new Ticket(order);
        firstTicket.setId(UUID.randomUUID());
        secondTicket = new Ticket(order);
        secondTicket.setId(UUID.randomUUID());

        mpPaymentMock = mock(Payment.class);

        localPayment = new org.gcrel.tfbackevent.model.Payment(
                "123456789",
                new BigDecimal("200.00"),
                LocalDateTime.now(),
                order);
    }

    @Test
    void handleNewPayment_WhenApproved_ActivatesAllTicketsAndSchedulesDelivery() {
        when(mpPaymentMock.getStatus()).thenReturn("approved");
        when(mpPaymentMock.getId()).thenReturn(123456789L);
        when(mpPaymentMock.getTransactionAmount()).thenReturn(new BigDecimal("200.00"));
        when(mpPaymentMock.getStatusDetail()).thenReturn("accredited");
        when(ticketRepository.findByOrder(order)).thenReturn(List.of(firstTicket, secondTicket));
        when(qrService.generateQRBase64(firstTicket.getId())).thenReturn("qr_1");
        when(qrService.generateQRBase64(secondTicket.getId())).thenReturn("qr_2");

        PurchaseResponse response = paymentService.handleNewPayment(order, mpPaymentMock);

        assertEquals(order.getId(), response.orderId());
        assertEquals("approved", response.status());
        assertEquals("accredited", response.statusDetail());
        assertEquals(2, response.tickets().size());
        assertEquals("qr_1", response.tickets().get(0).qrCodeBase64());
        assertEquals("qr_2", response.tickets().get(1).qrCodeBase64());
        assertEquals(TicketStatus.ACTIVE, firstTicket.getStatus());
        assertEquals(TicketStatus.ACTIVE, secondTicket.getStatus());
        assertEquals(DeliveryStatus.PENDING, order.getDeliveryStatus());

        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.APPROVED, paymentCaptor.getValue().getPaymentStatus());
        assertEquals(order, paymentCaptor.getValue().getOrder());
        verify(orderRepository).save(order);
    }

    @Test
    void handleNewPayment_WhenApprovedButQRFails_IgnoresExceptionAndReturnsNullQR() {
        when(mpPaymentMock.getStatus()).thenReturn("approved");
        when(mpPaymentMock.getId()).thenReturn(123456789L);
        when(ticketRepository.findByOrder(order)).thenReturn(List.of(firstTicket));
        when(qrService.generateQRBase64(firstTicket.getId())).thenThrow(new QRGenerationException());

        PurchaseResponse response = paymentService.handleNewPayment(order, mpPaymentMock);

        assertEquals("approved", response.status());
        assertNull(response.tickets().get(0).qrCodeBase64());
        assertEquals(TicketStatus.ACTIVE, firstTicket.getStatus());
        verify(paymentRepository).save(any());
    }

    @Test
    void handleNewPayment_WhenRejected_CancelsAllTicketsAndDoesNotCallQR() {
        when(mpPaymentMock.getStatus()).thenReturn("rejected");
        when(mpPaymentMock.getId()).thenReturn(123456789L);
        when(ticketRepository.findByOrder(order)).thenReturn(List.of(firstTicket, secondTicket));

        PurchaseResponse response = paymentService.handleNewPayment(order, mpPaymentMock);

        assertEquals("rejected", response.status());
        response.tickets().forEach(purchased -> assertNull(purchased.qrCodeBase64()));
        assertEquals(TicketStatus.CANCELLED, firstTicket.getStatus());
        assertEquals(TicketStatus.CANCELLED, secondTicket.getStatus());
        assertEquals(DeliveryStatus.NOT_REQUIRED, order.getDeliveryStatus());

        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.REJECTED, paymentCaptor.getValue().getPaymentStatus());
        verify(qrService, never()).generateQRBase64(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleExistingPayment_WhenPaymentNotPending_ReturnsEarly() {
        localPayment.setPaymentStatus(PaymentStatus.APPROVED);
        when(paymentRepository.findByTransactionId(localPayment.getTransactionId()))
                .thenReturn(Optional.of(localPayment));

        paymentService.handleExistingPayment(localPayment, mpPaymentMock);

        verify(paymentRepository, never()).save(any());
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void handleExistingPayment_WhenApproved_ActivatesPendingTicketsAndSchedulesDelivery() {
        localPayment.setPaymentStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByTransactionId(localPayment.getTransactionId()))
                .thenReturn(Optional.of(localPayment));
        when(mpPaymentMock.getStatus()).thenReturn("approved");
        when(ticketRepository.findByOrderAndStatus(order, TicketStatus.PENDING))
                .thenReturn(List.of(firstTicket, secondTicket));

        paymentService.handleExistingPayment(localPayment, mpPaymentMock);

        assertEquals(TicketStatus.ACTIVE, firstTicket.getStatus());
        assertEquals(TicketStatus.ACTIVE, secondTicket.getStatus());
        assertEquals(PaymentStatus.APPROVED, localPayment.getPaymentStatus());
        assertEquals(DeliveryStatus.PENDING, order.getDeliveryStatus());

        verify(ticketRepository).saveAll(List.of(firstTicket, secondTicket));
        verify(paymentRepository).save(localPayment);
        verify(orderRepository).save(order);
    }

    @Test
    void handleExistingPayment_WhenRejected_CancelsPendingTickets() {
        localPayment.setPaymentStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByTransactionId(localPayment.getTransactionId()))
                .thenReturn(Optional.of(localPayment));
        when(mpPaymentMock.getStatus()).thenReturn("rejected");
        when(ticketRepository.findByOrderAndStatus(order, TicketStatus.PENDING))
                .thenReturn(List.of(firstTicket, secondTicket));

        paymentService.handleExistingPayment(localPayment, mpPaymentMock);

        assertEquals(TicketStatus.CANCELLED, firstTicket.getStatus());
        assertEquals(TicketStatus.CANCELLED, secondTicket.getStatus());
        assertEquals(PaymentStatus.REJECTED, localPayment.getPaymentStatus());
        assertEquals(DeliveryStatus.NOT_REQUIRED, order.getDeliveryStatus());
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
        assertNotNull(localPayment.getNextReconcileAt());

        verify(paymentRepository).save(localPayment);
        verify(ticketRepository, never()).saveAll(any());
    }
}
