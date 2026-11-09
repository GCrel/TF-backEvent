package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.auth.service.dto.TicketQr;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.config.TicketDeliveryProperties;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.exception.TicketDeliveryException;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
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
    private TicketOrderRepository orderRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private QRService qrService;
    @Mock
    private EmailService emailService;
    @Mock
    private TicketDeliveryProperties deliveryProperties;

    @InjectMocks
    private DeliveryService deliveryService;

    @Captor
    private ArgumentCaptor<TicketOrder> orderCaptor;

    private User buyer;
    private TicketOrder orderDue;
    private Ticket firstTicket;
    private Ticket secondTicket;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setEmail("test@delivery.com");

        Event event = new Event("Test Event", "Event Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("100.00")) {
            @Override
            public org.gcrel.tfbackevent.model.enums.EventType getType() {
                return org.gcrel.tfbackevent.model.enums.EventType.IN_PERSON;
            }
        };

        orderDue = order(event, 2);
        firstTicket = activeTicket(orderDue);
        secondTicket = activeTicket(orderDue);

        // saveAndFlush devuelve la misma instancia (en producción, la copia
        // gestionada).
        lenient().when(orderRepository.saveAndFlush(any(TicketOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(deliveryProperties.maxAttempts()).thenReturn(8);
        lenient().when(deliveryProperties.initialBackoffMinutes()).thenReturn(1L);
        lenient().when(deliveryProperties.maxBackoffMinutes()).thenReturn(60L);
    }

    @Test
    void processDueDeliveries_WhenRecoveringStaleDeliveries_UpdatesStatusToFailed() {
        TicketOrder stale = order(orderDue.getEvent(), 1);
        stale.setDeliveryStatus(DeliveryStatus.PROCESSING);
        stubDueOrders();
        when(orderRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                eq(DeliveryStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of(stale));

        deliveryService.processDueDeliveries();

        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(DeliveryStatus.FAILED, orderCaptor.getValue().getDeliveryStatus());
        assertNotNull(orderCaptor.getValue().getNextDeliveryAttemptAt());
    }

    @Test
    void processDueDeliveries_WhenDeliverySuccessful_SendsOneEmailWithEveryQr() {
        stubDueOrders(orderDue);
        when(ticketRepository.findByOrderAndStatus(orderDue, TicketStatus.ACTIVE))
                .thenReturn(List.of(firstTicket, secondTicket));
        when(qrService.generateQRBase64(firstTicket.getId())).thenReturn("qr_1");
        when(qrService.generateQRBase64(secondTicket.getId())).thenReturn("qr_2");

        deliveryService.processDueDeliveries();

        verify(emailService).sendTicketEmail(buyer.getEmail(), orderDue, List.of(
                new TicketQr(firstTicket.getId(), "qr_1"),
                new TicketQr(secondTicket.getId(), "qr_2")));
        assertEquals(DeliveryStatus.SENT, orderDue.getDeliveryStatus());
        assertEquals(1, orderDue.getDeliveryAttempts());
        assertNull(orderDue.getLastDeliveryError());
    }

    @Test
    void processDueDeliveries_SavesTheInstanceReturnedBySaveAndFlush() {
        TicketOrder managedCopy = order(orderDue.getEvent(), 2);
        stubDueOrders(orderDue);
        when(orderRepository.saveAndFlush(orderDue)).thenReturn(managedCopy);
        when(ticketRepository.findByOrderAndStatus(managedCopy, TicketStatus.ACTIVE))
                .thenReturn(List.of(firstTicket));
        when(qrService.generateQRBase64(firstTicket.getId())).thenReturn("qr_1");

        deliveryService.processDueDeliveries();

        // Guardar la instancia original (versión desactualizada) causaría un
        // ObjectOptimisticLockingFailureException en una base real.
        verify(orderRepository).save(managedCopy);
        verify(orderRepository, never()).save(orderDue);
        assertEquals(DeliveryStatus.SENT, managedCopy.getDeliveryStatus());
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
        TicketOrder virtualOrder = order(virtualEvent, 2);
        stubDueOrders(virtualOrder);
        when(ticketRepository.findByOrderAndStatus(virtualOrder, TicketStatus.ACTIVE))
                .thenReturn(List.of(activeTicket(virtualOrder), activeTicket(virtualOrder)));

        deliveryService.processDueDeliveries();

        verify(emailService).sendVirtualTicketEmail(buyer.getEmail(), virtualOrder, 2);
        verify(emailService, never()).sendTicketEmail(any(), any(), anyList());
        verify(qrService, never()).generateQRBase64(any());
        assertEquals(DeliveryStatus.SENT, virtualOrder.getDeliveryStatus());
    }

    @Test
    void processDueDeliveries_WhenNoActiveTickets_SkipsEmail() {
        stubDueOrders(orderDue);
        when(ticketRepository.findByOrderAndStatus(orderDue, TicketStatus.ACTIVE)).thenReturn(List.of());

        deliveryService.processDueDeliveries();

        verify(emailService, never()).sendTicketEmail(any(), any(), anyList());
        verify(emailService, never()).sendVirtualTicketEmail(any(), any(), anyInt());
        assertEquals(DeliveryStatus.NOT_REQUIRED, orderDue.getDeliveryStatus());
    }

    @Test
    void processDueDeliveries_WhenQRGenerationFails_SetsStatusToFailedAndSchedulesRetry() {
        stubDueOrders(orderDue);
        when(ticketRepository.findByOrderAndStatus(orderDue, TicketStatus.ACTIVE))
                .thenReturn(List.of(firstTicket));
        when(qrService.generateQRBase64(firstTicket.getId())).thenThrow(new QRGenerationException());

        deliveryService.processDueDeliveries();

        verify(orderRepository).save(orderCaptor.capture());
        TicketOrder failed = orderCaptor.getValue();
        assertEquals(DeliveryStatus.FAILED, failed.getDeliveryStatus());
        assertEquals(1, failed.getDeliveryAttempts());
        assertEquals("Error al generar el código QR.", failed.getLastDeliveryError());
        verify(emailService, never()).sendTicketEmail(any(), any(), anyList());
    }

    @Test
    void processDueDeliveries_WhenEmailServiceFails_SetsStatusToFailed() {
        stubDueOrders(orderDue);
        when(ticketRepository.findByOrderAndStatus(orderDue, TicketStatus.ACTIVE))
                .thenReturn(List.of(firstTicket));
        when(qrService.generateQRBase64(firstTicket.getId())).thenReturn("qr_1");
        doThrow(new TicketDeliveryException("Email Error", new RuntimeException("SMTP Timeout")))
                .when(emailService).sendTicketEmail(anyString(), any(), anyList());

        deliveryService.processDueDeliveries();

        verify(orderRepository).save(orderCaptor.capture());
        TicketOrder failed = orderCaptor.getValue();
        assertEquals(DeliveryStatus.FAILED, failed.getDeliveryStatus());
        assertEquals(1, failed.getDeliveryAttempts());
        assertEquals("Email Error", failed.getLastDeliveryError());
    }

    @Test
    void requestDelivery_WhenNotSent_SchedulesDelivery() {
        orderDue.setDeliveryStatus(DeliveryStatus.FAILED);
        when(orderRepository.findById(orderDue.getId())).thenReturn(Optional.of(orderDue));

        deliveryService.requestDelivery(orderDue.getId());

        assertEquals(DeliveryStatus.PENDING, orderDue.getDeliveryStatus());
        assertNotNull(orderDue.getNextDeliveryAttemptAt());
        verify(orderRepository).save(orderDue);
    }

    @Test
    void requestDelivery_WhenSentRecently_DoesNothing() {
        orderDue.setDeliveryStatus(DeliveryStatus.SENT);
        orderDue.setNextDeliveryAttemptAt(LocalDateTime.now().plusMinutes(1));
        when(orderRepository.findById(orderDue.getId())).thenReturn(Optional.of(orderDue));

        deliveryService.requestDelivery(orderDue.getId());

        assertEquals(DeliveryStatus.SENT, orderDue.getDeliveryStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void requestDelivery_WhenCooldownElapsed_SchedulesDelivery() {
        orderDue.setDeliveryStatus(DeliveryStatus.SENT);
        orderDue.setNextDeliveryAttemptAt(LocalDateTime.now().minusSeconds(1));
        when(orderRepository.findById(orderDue.getId())).thenReturn(Optional.of(orderDue));

        deliveryService.requestDelivery(orderDue.getId());

        assertEquals(DeliveryStatus.PENDING, orderDue.getDeliveryStatus());
        verify(orderRepository).save(orderDue);
    }

    @Test
    void processDueDeliveries_WhenFailing_RetriesWithExponentialBackoff() {
        // Intentos previos -> espera esperada tras el siguiente fallo: 1, 2, 4, 8...
        // minutos.
        int[] previousAttempts = { 0, 1, 2, 3 };
        long[] expectedMinutes = { 1, 2, 4, 8 };
        for (int i = 0; i < previousAttempts.length; i++) {
            orderDue.scheduleDelivery(LocalDateTime.now());
            orderDue.setDeliveryAttempts(previousAttempts[i]);

            failNextDelivery();

            assertEquals(DeliveryStatus.FAILED, orderDue.getDeliveryStatus());
            assertRetryIn(orderDue, expectedMinutes[i]);
        }
    }

    @Test
    void processDueDeliveries_BackoffIsCappedAtMaximum() {
        orderDue.setDeliveryAttempts(6); // el 7mo intento esperaría 64 min sin tope

        failNextDelivery();

        assertRetryIn(orderDue, 60);
    }

    @Test
    void processDueDeliveries_WhenMaxAttemptsReached_StopsRetrying() {
        orderDue.setDeliveryAttempts(7); // este es el 8vo y último intento

        failNextDelivery();

        assertEquals(DeliveryStatus.FAILED, orderDue.getDeliveryStatus());
        assertEquals(8, orderDue.getDeliveryAttempts());
        assertNull(orderDue.getNextDeliveryAttemptAt());
    }

    @Test
    void processDueDeliveries_WhenStaleDeliveryExhaustsAttempts_StopsRetrying() {
        TicketOrder stale = order(orderDue.getEvent(), 1);
        stale.setDeliveryStatus(DeliveryStatus.PROCESSING);
        stale.setDeliveryAttempts(8);
        stubDueOrders();
        when(orderRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                eq(DeliveryStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of(stale));

        deliveryService.processDueDeliveries();

        assertEquals(DeliveryStatus.FAILED, stale.getDeliveryStatus());
        assertNull(stale.getNextDeliveryAttemptAt());
    }

    @Test
    void processDueDeliveries_WhenOneOrderFailsUnexpectedly_ContinuesWithTheRest() {
        TicketOrder broken = order(orderDue.getEvent(), 1);
        stubDueOrders(broken, orderDue);
        when(ticketRepository.findByOrderAndStatus(broken, TicketStatus.ACTIVE))
                .thenThrow(new IllegalStateException("boom"));
        when(ticketRepository.findByOrderAndStatus(orderDue, TicketStatus.ACTIVE))
                .thenReturn(List.of(firstTicket));
        when(qrService.generateQRBase64(firstTicket.getId())).thenReturn("qr_1");

        deliveryService.processDueDeliveries();

        assertEquals(DeliveryStatus.SENT, orderDue.getDeliveryStatus());
    }

    @Test
    void requestDelivery_WhenAttemptsExhausted_StartsANewCycle() {
        orderDue.setDeliveryStatus(DeliveryStatus.FAILED);
        orderDue.setDeliveryAttempts(8);
        orderDue.setNextDeliveryAttemptAt(null);
        when(orderRepository.findById(orderDue.getId())).thenReturn(Optional.of(orderDue));

        deliveryService.requestDelivery(orderDue.getId());

        assertEquals(DeliveryStatus.PENDING, orderDue.getDeliveryStatus());
        assertEquals(0, orderDue.getDeliveryAttempts());
        assertNotNull(orderDue.getNextDeliveryAttemptAt());
    }

    @Test
    void requestDelivery_WhenDeliveryInProgress_DoesNothing() {
        orderDue.setDeliveryStatus(DeliveryStatus.PROCESSING);
        when(orderRepository.findById(orderDue.getId())).thenReturn(Optional.of(orderDue));

        deliveryService.requestDelivery(orderDue.getId());

        assertEquals(DeliveryStatus.PROCESSING, orderDue.getDeliveryStatus());
        verify(orderRepository, never()).save(any());
    }

    private void failNextDelivery() {
        stubDueOrders(orderDue);
        when(ticketRepository.findByOrderAndStatus(orderDue, TicketStatus.ACTIVE))
                .thenReturn(List.of(firstTicket));
        // doThrow evita invocar el mock al volver a hacer stub dentro de un bucle.
        doThrow(new QRGenerationException()).when(qrService).generateQRBase64(firstTicket.getId());

        deliveryService.processDueDeliveries();
    }

    private void assertRetryIn(TicketOrder order, long minutes) {
        LocalDateTime expected = LocalDateTime.now().plusMinutes(minutes);
        LocalDateTime next = order.getNextDeliveryAttemptAt();
        assertNotNull(next);
        assertTrue(Math.abs(java.time.Duration.between(expected, next).toSeconds()) < 5,
                "Se esperaba reintento en " + minutes + " min, pero fue " + next);
    }

    private void stubDueOrders(TicketOrder... orders) {
        lenient().when(orderRepository.findByDeliveryStatusAndNextDeliveryAttemptAtBefore(
                eq(DeliveryStatus.PROCESSING), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(orderRepository.findTop50ByDeliveryStatusInAndNextDeliveryAttemptAtLessThanEqual(
                any(), any(LocalDateTime.class)))
                .thenReturn(List.of(orders));
    }

    private TicketOrder order(Event event, int quantity) {
        TicketOrder order = new TicketOrder(buyer, event, quantity, event.getBasePrice(), LocalDateTime.now());
        order.setId(UUID.randomUUID());
        order.scheduleDelivery(LocalDateTime.now());
        return order;
    }

    private Ticket activeTicket(TicketOrder order) {
        Ticket ticket = new Ticket(order);
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(TicketStatus.ACTIVE);
        return ticket;
    }
}
