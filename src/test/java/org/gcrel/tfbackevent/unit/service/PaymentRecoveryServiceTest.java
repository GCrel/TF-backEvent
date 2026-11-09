package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.payment.exception.PaymentProcessingException;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.service.MercadoPagoService;
import org.gcrel.tfbackevent.payment.service.MpOAuthService;
import org.gcrel.tfbackevent.payment.service.PaymentRecoveryService;
import org.gcrel.tfbackevent.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketOrderRepository orderRepository;
    @Mock
    private EventParticipationRepository eventParticipationRepository;
    @Mock
    private MpOAuthService mpOAuthService;
    @Mock
    private MercadoPagoService mercadoPagoService;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentRecoveryService service;

    private User organizer;
    private InPersonEvent event;
    private Payment payment;

    @BeforeEach
    void setUp() {
        organizer = new User("Org", "org@example.com", "hash", LocalDate.of(1990, 1, 1));
        User buyer = new User("Bruno", "bruno@example.com", "hash", LocalDate.of(1995, 1, 1));
        event = new InPersonEvent("Conf", "Desc", LocalDateTime.now().plusDays(3), new BigDecimal("10.00"),
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 10);
        TicketOrder order = new TicketOrder(buyer, event, 1, new BigDecimal("10.00"), LocalDateTime.now());
        payment = new Payment("tx-1", new BigDecimal("10.00"), LocalDateTime.now().minusMinutes(30), order);
    }

    private void stubOrganizer() {
        when(eventParticipationRepository.findByEventAndId_EventRole(event, EventRole.ORGANIZER))
                .thenReturn(Optional.of(new EventParticipation(organizer, event, EventRole.ORGANIZER)));
        when(mpOAuthService.getValidAccessToken(organizer)).thenReturn("organizer-token");
    }

    @Test
    void reconcile_ConsultsMercadoPagoWithTheOrganizerTokenAndUpdatesThePayment() {
        stubOrganizer();
        var mpPayment = new com.mercadopago.resources.payment.Payment();
        when(mercadoPagoService.getPaymentById("tx-1", "organizer-token")).thenReturn(mpPayment);

        service.reconcile(payment);

        verify(paymentService).handleExistingPayment(payment, mpPayment);
    }

    @Test
    void reconcile_WhenMercadoPagoFails_PropagatesWithoutUpdating() {
        stubOrganizer();
        when(mercadoPagoService.getPaymentById("tx-1", "organizer-token"))
                .thenThrow(new PaymentProcessingException("timeout", null));

        assertThrows(PaymentProcessingException.class, () -> service.reconcile(payment));

        verify(paymentService, never()).handleExistingPayment(any(), any());
    }

    @Test
    void reconcile_WhenTheEventHasNoOrganizer_Throws() {
        when(eventParticipationRepository.findByEventAndId_EventRole(event, EventRole.ORGANIZER))
                .thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.reconcile(payment));
    }

    // El scheduler usa la misma lógica y, si falla, pospone el pago en vez de propagar.
    @Test
    void recoverPayments_WhenReconcilingFails_PostponesThePayment() {
        stubOrganizer();
        when(paymentRepository.findPendingForReconciliation(
                any(PaymentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(payment));
        when(mercadoPagoService.getPaymentById("tx-1", "organizer-token"))
                .thenThrow(new PaymentProcessingException("timeout", null));
        when(orderRepository.findPendingOrphans(any(), any())).thenReturn(List.of());

        service.recoverPayments();

        verify(paymentRepository).postponeReconciliation(any(), any());
    }
}
