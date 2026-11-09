package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.event.config.EventWindowProperties;
import org.gcrel.tfbackevent.event.exception.EventNotAvailableException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.event.repository.SectorRepository;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Sector;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.config.TicketPurchaseProperties;
import org.gcrel.tfbackevent.payment.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.exception.EventCapacityExceededException;
import org.gcrel.tfbackevent.payment.exception.EventPriceChangedException;
import org.gcrel.tfbackevent.payment.exception.InvalidTicketPurchaseException;
import org.gcrel.tfbackevent.payment.exception.SectorCapacityExceededException;
import org.gcrel.tfbackevent.payment.exception.PaymentProcessingException;
import org.gcrel.tfbackevent.payment.exception.PaymentRejectedException;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.UnauthorizedTicketAccessException;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.service.DeliveryService;
import org.gcrel.tfbackevent.payment.service.MercadoPagoService;
import org.gcrel.tfbackevent.payment.service.MpOAuthService;
import org.gcrel.tfbackevent.payment.service.PaymentService;
import org.gcrel.tfbackevent.payment.service.QRService;
import org.gcrel.tfbackevent.payment.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.mercadopago.resources.payment.Payment;

@ExtendWith(MockitoExtension.class)
public class TicketServiceTest {
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketOrderRepository orderRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventParticipationRepository eventParticipationRepository;
    @Mock
    private MpOAuthService mpOAuthService;
    @Mock
    private MercadoPagoService mercadoPagoService;
    @Mock
    private QRService qrService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private DeliveryService deliveryService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private EventWindowProperties eventWindowProperties;
    @Mock
    private TicketPurchaseProperties purchaseProperties;

    @Mock
    private SectorRepository sectorRepository;

    @InjectMocks
    private TicketService ticketService;

    private User attendee;
    private User organizer;
    private EventParticipation organizerParticipation;
    private InPersonEvent event;
    private PurchaseTicketRequest purchaseRequest;
    private Payment mpPaymentMock;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        attendee = new User();
        attendee.setId(UUID.randomUUID());
        attendee.setEmail("test@test.com");

        organizer = new User();
        organizer.setId(UUID.randomUUID());
        organizer.setEmail("organizer@test.com");

        event = new InPersonEvent(
                "Test Event",
                "Description",
                LocalDateTime.now().plusDays(1),
                new BigDecimal("1000.00"),
                "Venue",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                100);
        event.setId(UUID.randomUUID());
        event.setStatus(EventStatus.PUBLISHED);

        organizerParticipation = new EventParticipation(organizer, event, EventRole.ORGANIZER);

        purchaseRequest = purchaseRequest(1);

        mpPaymentMock = mock(Payment.class);
        lenient().when(eventWindowProperties.afterHours()).thenReturn(12L);
        lenient().when(purchaseProperties.maxQuantity()).thenReturn(10);

        TicketOrder order = new TicketOrder(attendee, event, 1, event.getBasePrice(), LocalDateTime.now());
        order.setId(UUID.randomUUID());
        ticket = new Ticket(order);
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(TicketStatus.ACTIVE);

        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        lenient().when(orderRepository.save(any(TicketOrder.class))).thenAnswer(invocation -> {
            TicketOrder saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void purchase_WhenPaymentApproved_ReturnsApprovedResponse() {
        stubPurchasableEvent();
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(mpPaymentMock);
        PurchaseResponse expected = new PurchaseResponse(
                UUID.randomUUID(), "approved", null, "approved",
                List.of(new PurchaseResponse.PurchasedTicket(ticket.getId(), "qr_base64_string")));
        when(paymentService.handleNewPayment(any(TicketOrder.class), eq(mpPaymentMock))).thenReturn(expected);

        PurchaseResponse response = ticketService.purchase(purchaseRequest, attendee);

        assertEquals("approved", response.status());
        assertEquals("qr_base64_string", response.tickets().get(0).qrCodeBase64());
    }

    @Test
    void purchase_WithQuantity_CreatesOneTicketPerUnitAndChargesTotal() {
        stubPurchasableEvent();
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(mpPaymentMock);

        ticketService.purchase(purchaseRequest(3), attendee);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> ticketsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(ticketsCaptor.capture());
        assertEquals(3, ticketsCaptor.getValue().size());
        ticketsCaptor.getValue().forEach(created -> {
            assertEquals(TicketStatus.PENDING, created.getStatus());
            assertEquals(attendee, created.getAttendee());
        });

        ArgumentCaptor<TicketOrder> orderCaptor = ArgumentCaptor.forClass(TicketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        TicketOrder order = orderCaptor.getValue();
        assertEquals(3, order.getQuantity());
        assertEquals(0, new BigDecimal("3000.00").compareTo(order.getTotalAmount()));

        verify(mercadoPagoService).processPayment(
                any(), eq(order.getTotalAmount()), anyInt(), any(), any(), any(), eq(order.getId().toString()));
    }

    @Test
    void purchase_WhenUserAlreadyHasTickets_IsAllowed() {
        stubPurchasableEvent();
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(mpPaymentMock);

        ticketService.purchase(purchaseRequest, attendee);
        ticketService.purchase(purchaseRequest, attendee);

        verify(orderRepository, org.mockito.Mockito.times(2)).save(any(TicketOrder.class));
    }

    @Test
    void purchase_WhenQuantityExceedsMax_ThrowsAndDoesNotReserve() {
        assertThrows(
                InvalidTicketPurchaseException.class,
                () -> ticketService.purchase(purchaseRequest(11), attendee));

        verify(orderRepository, never()).save(any());
        verify(ticketRepository, never()).saveAll(anyList());
    }

    @Test
    void purchase_WhenQuantityExceedsRemainingCapacity_ThrowsCapacityExceeded() {
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventRepository.findByIdForUpdate(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventParticipationRepository.findByEventAndId_EventRole(event, EventRole.ORGANIZER))
                .thenReturn(Optional.of(organizerParticipation));
        when(mpOAuthService.getValidAccessToken(organizer)).thenReturn("ORGANIZER-TOKEN");
        when(ticketRepository.countByEventAndStatusIn(eq(event), anyList())).thenReturn(98L);

        assertThrows(
                EventCapacityExceededException.class,
                () -> ticketService.purchase(purchaseRequest(3), attendee));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void purchase_WhenEventNotPublished_ThrowsException() {
        event.setStatus(EventStatus.DRAFT);
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));

        assertThrows(
                EventNotAvailableException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void purchase_WhenEventIsTenDaysAway_IsAllowed() {
        event.setDateTime(LocalDateTime.now().plusDays(10));
        stubPurchasableEvent();
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(mpPaymentMock);

        ticketService.purchase(purchaseRequest, attendee);

        verify(paymentService).handleNewPayment(any(TicketOrder.class), eq(mpPaymentMock));
    }

    @Test
    void purchase_WhenEventEndedThirteenHoursAgo_ThrowsException() {
        event.setDateTime(LocalDateTime.now().minusHours(13));
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));

        assertThrows(
                EventNotAvailableException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void purchase_WhenPaymentRejectedExceptionThrown_CancelsReservationAndThrows() {
        stubPurchasableEvent();
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenThrow(new PaymentRejectedException("Rejected"));

        assertThrows(
                PaymentRejectedException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(ticketRepository).cancelPendingWithoutPayment(
                any(TicketOrder.class), eq(TicketStatus.PENDING), eq(TicketStatus.CANCELLED));
        verify(paymentService, never()).handleNewPayment(any(), any());
    }

    @Test
    void purchase_WhenPaymentOutcomeUnknown_KeepsReservationPending() {
        stubPurchasableEvent();
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenThrow(new PaymentProcessingException("timeout", new RuntimeException(), true));

        assertThrows(
                PaymentProcessingException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(ticketRepository, never()).cancelPendingWithoutPayment(any(), any(), any());
    }

    @Test
    void resendTicketEmail_WhenValid_RequestsOrderDelivery() {
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        ticketService.resendTicketEmail(ticket.getId(), attendee);

        verify(deliveryService).requestDelivery(ticket.getOrder().getId());
    }

    @Test
    void resendTicketEmail_WhenUnauthorizedUser_ThrowsException() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(
                UnauthorizedTicketAccessException.class,
                () -> ticketService.resendTicketEmail(ticket.getId(), otherUser));

        verify(deliveryService, never()).requestDelivery(any());
    }

    @Test
    void resendTicketEmail_WhenTicketNotActive_ThrowsException() {
        ticket.setStatus(TicketStatus.CANCELLED);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(
                TicketNotActiveException.class,
                () -> ticketService.resendTicketEmail(ticket.getId(), attendee));
    }

    @Test
    void purchase_WithSector_ChargesTheSectorPriceAndLinksTheOrder() {
        Sector vip = sector(event, "15000.00", 10);
        stubPurchasableEvent();
        when(sectorRepository.findById(vip.getId())).thenReturn(Optional.of(vip));
        when(sectorRepository.countTickets(eq(vip), any())).thenReturn(8L);
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(mpPaymentMock);

        ticketService.purchase(sectorRequest(vip, 2, true), attendee);

        ArgumentCaptor<TicketOrder> orderCaptor = ArgumentCaptor.forClass(TicketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        TicketOrder order = orderCaptor.getValue();
        assertEquals(vip, order.getSector());
        assertEquals(0, new BigDecimal("30000.00").compareTo(order.getTotalAmount()));
        verify(mercadoPagoService).processPayment(
                any(), eq(order.getTotalAmount()), anyInt(), any(), any(), any(), anyString());
        // El cupo se mide por sector, no por evento.
        verify(ticketRepository, never()).countByEventAndStatusIn(any(), any());
    }

    @Test
    void purchase_WhenSectorIsFull_ThrowsConflictWithoutCharging() {
        Sector vip = sector(event, "15000.00", 10);
        stubPurchasableEvent();
        when(sectorRepository.findById(vip.getId())).thenReturn(Optional.of(vip));
        when(sectorRepository.countTickets(eq(vip), any())).thenReturn(9L);

        assertThrows(SectorCapacityExceededException.class,
                () -> ticketService.purchase(sectorRequest(vip, 2, true), attendee));
        verify(orderRepository, never()).save(any());
        verify(mercadoPagoService, never()).processPayment(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void purchase_WithFreeSector_IssuesTicketsWithoutPaymentData() {
        Sector free = sector(event, "0.00", 50);
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventRepository.findByIdForUpdate(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(sectorRepository.findById(free.getId())).thenReturn(Optional.of(free));
        when(ticketRepository.findByOrder(any(TicketOrder.class))).thenReturn(List.of());

        PurchaseResponse response = ticketService.purchase(sectorRequest(free, 1, false), attendee);

        assertEquals("free", response.statusDetail());
        verify(mpOAuthService, never()).getValidAccessToken(any(User.class));
    }

    @Test
    void purchase_WhenEventHasSectorsButNoneIsChosen_ThrowsBadRequest() {
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(sectorRepository.existsByEvent(event)).thenReturn(true);

        assertThrows(InvalidTicketPurchaseException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void purchase_WithSectorOfAnotherEvent_ThrowsBadRequest() {
        InPersonEvent other = new InPersonEvent("Otro", "Description", event.getDateTime(), BigDecimal.TEN,
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        other.setId(UUID.randomUUID());
        Sector foreign = sector(other, "100.00", 10);
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(sectorRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(InvalidTicketPurchaseException.class,
                () -> ticketService.purchase(sectorRequest(foreign, 1, true), attendee));
    }

    private Sector sector(InPersonEvent owner, String price, int capacity) {
        Sector sector = new Sector(owner);
        sector.setId(UUID.randomUUID());
        sector.setName("VIP");
        sector.setPrice(new BigDecimal(price));
        sector.setCapacity(capacity);
        return sector;
    }

    private PurchaseTicketRequest sectorRequest(Sector sector, int quantity, boolean withPayment) {
        return withPayment
                ? new PurchaseTicketRequest(purchaseRequest.eventId(), quantity, sector.getId(), "token123", 1, "test@test.com", "visa")
                : new PurchaseTicketRequest(purchaseRequest.eventId(), quantity, sector.getId(), null, null, null, null);
    }

    private void stubPurchasableEvent() {
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventRepository.findByIdForUpdate(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventParticipationRepository.findByEventAndId_EventRole(event, EventRole.ORGANIZER))
                .thenReturn(Optional.of(organizerParticipation));
        when(mpOAuthService.getValidAccessToken(organizer)).thenReturn("ORGANIZER-TOKEN");
    }

    private PurchaseTicketRequest purchaseRequest(int quantity) {
        UUID eventId = purchaseRequest == null ? UUID.randomUUID() : purchaseRequest.eventId();
        return new PurchaseTicketRequest(eventId, quantity, null, "token123", 1, "test@test.com", "visa");
    }

    @Test
    void purchase_WhenEventIsFree_IssuesActiveTicketsWithoutMercadoPago() {
        event.setBasePrice(BigDecimal.ZERO);
        PurchaseTicketRequest freeRequest = new PurchaseTicketRequest(
                purchaseRequest.eventId(), 2, null, null, null, null, null);
        when(eventRepository.findById(freeRequest.eventId())).thenReturn(Optional.of(event));
        when(eventRepository.findByIdForUpdate(freeRequest.eventId())).thenReturn(Optional.of(event));
        when(ticketRepository.findByOrder(any(TicketOrder.class))).thenAnswer(invocation -> {
            TicketOrder order = invocation.getArgument(0);
            return List.of(activeTicket(order), activeTicket(order));
        });
        when(qrService.generateQRBase64(any(UUID.class))).thenReturn("qr");

        PurchaseResponse response = ticketService.purchase(freeRequest, attendee);

        assertEquals("approved", response.status());
        assertEquals("free", response.statusDetail());
        assertEquals(2, response.tickets().size());
        response.tickets().forEach(purchased -> assertEquals("qr", purchased.qrCodeBase64()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> ticketsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(ticketsCaptor.capture());
        ticketsCaptor.getValue().forEach(created -> assertEquals(TicketStatus.ACTIVE, created.getStatus()));

        ArgumentCaptor<TicketOrder> orderCaptor = ArgumentCaptor.forClass(TicketOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(DeliveryStatus.PENDING, orderCaptor.getValue().getDeliveryStatus());

        verify(mpOAuthService, never()).getValidAccessToken(any(User.class));
        verify(mercadoPagoService, never()).processPayment(any(), any(), any(), any(), any(), any(), any());
        verify(paymentService, never()).handleNewPayment(any(), any());
    }

    @Test
    void purchase_WhenEventIsPaidAndPaymentDataMissing_ThrowsBadRequest() {
        PurchaseTicketRequest withoutPayment = new PurchaseTicketRequest(
                purchaseRequest.eventId(), 1, null, null, null, null, null);
        when(eventRepository.findById(withoutPayment.eventId())).thenReturn(Optional.of(event));

        assertThrows(
                InvalidTicketPurchaseException.class,
                () -> ticketService.purchase(withoutPayment, attendee));

        verify(orderRepository, never()).save(any());
        verify(mercadoPagoService, never()).processPayment(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void purchase_WhenPriceChangesBeforeReservation_ThrowsConflict() {
        InPersonEvent lockedEvent = new InPersonEvent(
                "Test Event", "Description", event.getDateTime(), BigDecimal.ZERO,
                "Venue", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        lockedEvent.setStatus(EventStatus.PUBLISHED);
        stubPurchasableEvent();
        when(eventRepository.findByIdForUpdate(purchaseRequest.eventId())).thenReturn(Optional.of(lockedEvent));

        assertThrows(
                EventPriceChangedException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(orderRepository, never()).save(any());
        verify(mercadoPagoService, never()).processPayment(any(), any(), any(), any(), any(), any(), any());
    }

    private Ticket activeTicket(TicketOrder order) {
        Ticket created = new Ticket(order);
        created.setId(UUID.randomUUID());
        created.setStatus(TicketStatus.ACTIVE);
        return created;
    }
}
