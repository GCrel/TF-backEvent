package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.event.exception.EventNotAvailableException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.exception.PaymentRejectedException;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.UnauthorizedTicketAccessException;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.service.MercadoPagoService;
import org.gcrel.tfbackevent.payment.service.MpOAuthService;
import org.gcrel.tfbackevent.payment.service.PaymentService;
import org.gcrel.tfbackevent.payment.service.QRService;
import org.gcrel.tfbackevent.payment.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private PaymentRepository paymentRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventParticipationRepository eventParticipationRepository;
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
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private TicketService ticketService;

    private User attendee;
    private User organizer;
    private EventParticipation organizerParticipation;
    private Event event;
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

        event = new Event(
                "Test Event",
                "Description",
                LocalDateTime.now().plusDays(1),
                new BigDecimal("1000.00")) {
        };
        event.setId(UUID.randomUUID());
        event.setStatus(EventStatus.PUBLISHED);

        organizerParticipation = new EventParticipation(organizer, event, EventRole.ORGANIZER);

        purchaseRequest = new PurchaseTicketRequest(
                UUID.randomUUID(), "token123", 1, "test@test.com", "visa");

        mpPaymentMock = mock(Payment.class);

        ticket = new Ticket(LocalDateTime.now(), event, attendee);
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(TicketStatus.ACTIVE);

        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void purchase_WhenPaymentApproved_ReturnsApprovedResponse() {
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventRepository.findForTicketReservation(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventParticipationRepository.findByEventAndEventRole(event, EventRole.ORGANIZER))
                .thenReturn(Optional.of(organizerParticipation));
        when(mpOAuthService.getValidAccessToken(organizer)).thenReturn("ORGANIZER-TOKEN");

        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(mpPaymentMock);
        PurchaseResponse expected = new PurchaseResponse(
                ticket.getId(), "approved", null, "approved", "qr_base64_string");
        when(paymentService.handleNewPayment(any(), eq(mpPaymentMock), eq(attendee))).thenReturn(expected);

        PurchaseResponse response = ticketService.purchase(purchaseRequest, attendee);

        assertEquals("approved", response.status());
        assertEquals("qr_base64_string", response.qrCodeBase64());

        verify(paymentService).handleNewPayment(any(Ticket.class), eq(mpPaymentMock), eq(attendee));
    }

    @Test
    void purchase_WhenEventNotPublished_ThrowsException() {
        event.setStatus(EventStatus.DRAFT);
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));

        assertThrows(
                EventNotAvailableException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void purchase_WhenPaymentRejectedExceptionThrown_CancelsTicketAndThrows() {
        when(eventRepository.findById(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventRepository.findForTicketReservation(purchaseRequest.eventId())).thenReturn(Optional.of(event));
        when(eventParticipationRepository.findByEventAndEventRole(event, EventRole.ORGANIZER))
                .thenReturn(Optional.of(organizerParticipation));
        when(mpOAuthService.getValidAccessToken(organizer)).thenReturn("ORGANIZER-TOKEN");
        when(mercadoPagoService.processPayment(any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new PaymentRejectedException("Rejected"));

        assertThrows(
                PaymentRejectedException.class,
                () -> ticketService.purchase(purchaseRequest, attendee));

        verify(ticketRepository, times(2))
                .save(
                        argThat(
                                t -> t.getStatus() == TicketStatus.CANCELLED
                                        || t.getStatus() == null));
    }

    @Test
    void resendTicketEmail_WhenValid_SendsEmail() {
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        ticketService.resendTicketEmail(ticket.getId(), attendee);

        verify(paymentService).requestDelivery(ticket);
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
