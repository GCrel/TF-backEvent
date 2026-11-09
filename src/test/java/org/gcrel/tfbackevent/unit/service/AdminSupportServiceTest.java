package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.exception.AdminConflictException;
import org.gcrel.tfbackevent.admin.exception.PaymentNotFoundException;
import org.gcrel.tfbackevent.admin.service.AdminSupportService;
import org.gcrel.tfbackevent.admin.service.AuditService;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.exception.PaymentProcessingException;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.service.DeliveryService;
import org.gcrel.tfbackevent.payment.service.PaymentRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminSupportServiceTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private PaymentRecoveryService paymentRecoveryService;
    @Mock
    private DeliveryService deliveryService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AdminSupportService service;

    private User actor;
    private User buyer;
    private InPersonEvent event;
    private TicketOrder order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        actor = new User("Ana", "ana@example.com", "hash", LocalDate.of(1990, 1, 1));
        buyer = new User("Bruno", "bruno@example.com", "hash", LocalDate.of(1995, 1, 1));
        buyer.setId(UUID.randomUUID());
        event = new InPersonEvent("Conf", "Desc", LocalDateTime.now().plusDays(3), new BigDecimal("10.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 10);
        event.setId(UUID.randomUUID());
        order = new TicketOrder(buyer, event, 1, new BigDecimal("10.00"), LocalDateTime.now());
        order.setId(UUID.randomUUID());
        payment = new Payment("tx-1", new BigDecimal("10.00"), LocalDateTime.now().minusMinutes(30), order);
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
    }

    @Test
    void reconcile_UpdatesThePaymentAndAuditsTheTransition() {
        when(paymentRepository.findWithDetailsById(payment.getId())).thenReturn(Optional.of(payment));
        doAnswer(inv -> {
            payment.setPaymentStatus(PaymentStatus.APPROVED);
            return null;
        }).when(paymentRecoveryService).reconcile(payment);

        var response = service.reconcile(actor, payment.getId());

        assertEquals(PaymentStatus.APPROVED, response.status());
        assertEquals("Conf", response.eventTitle());
        verify(auditService).record(actor, AuditAction.PAYMENT_RECONCILED, AuditTargetType.PAYMENT,
                payment.getId(), "PENDING -> APPROVED");
    }

    @Test
    void reconcile_WhenPaymentIsNotPending_ThrowsConflictWithoutCallingMercadoPago() {
        payment.setPaymentStatus(PaymentStatus.APPROVED);
        when(paymentRepository.findWithDetailsById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(AdminConflictException.class, () -> service.reconcile(actor, payment.getId()));

        verify(paymentRecoveryService, never()).reconcile(any());
    }

    @Test
    void reconcile_WhenMissing_ThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findWithDetailsById(id)).thenReturn(Optional.empty());
        assertThrows(PaymentNotFoundException.class, () -> service.reconcile(actor, id));
    }

    @Test
    void reconcile_WhenMercadoPagoFails_PropagatesAndAuditsNothing() {
        when(paymentRepository.findWithDetailsById(payment.getId())).thenReturn(Optional.of(payment));
        doThrow(new PaymentProcessingException("timeout", null)).when(paymentRecoveryService).reconcile(payment);

        assertThrows(PaymentProcessingException.class, () -> service.reconcile(actor, payment.getId()));

        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    private Ticket activeTicket() {
        Ticket ticket = new Ticket(order);
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(TicketStatus.ACTIVE);
        return ticket;
    }

    @Test
    void resendDelivery_RequestsTheOrderDeliveryAndAudits() {
        Ticket ticket = activeTicket();
        when(ticketRepository.findWithEventAndAttendeeById(ticket.getId())).thenReturn(Optional.of(ticket));

        service.resendDelivery(actor, ticket.getId());

        verify(deliveryService).requestDelivery(order.getId());
        verify(auditService).record(actor, AuditAction.DELIVERY_REQUESTED, AuditTargetType.TICKET,
                ticket.getId(), "Orden " + order.getId());
    }

    @Test
    void resendDelivery_WhenTicketIsNotActive_Throws() {
        Ticket ticket = activeTicket();
        ticket.setStatus(TicketStatus.CANCELLED);
        when(ticketRepository.findWithEventAndAttendeeById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(TicketNotActiveException.class, () -> service.resendDelivery(actor, ticket.getId()));

        verify(deliveryService, never()).requestDelivery(any());
    }

    @Test
    void resendDelivery_WhenMissing_ThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(ticketRepository.findWithEventAndAttendeeById(id)).thenReturn(Optional.empty());
        assertThrows(TicketNotFoundException.class, () -> service.resendDelivery(actor, id));
    }
}
