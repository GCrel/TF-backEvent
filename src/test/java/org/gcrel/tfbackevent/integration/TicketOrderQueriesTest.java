package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.repository.dto.EventTicketCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.EntityManager;

// Verifica contra PostgreSQL las consultas que operan sobre órdenes de compra.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TicketOrderQueriesTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventParticipationRepository participationRepository;
    @Autowired
    private OrganizerMpAccountRepository mpAccountRepository;
    @Autowired
    private TicketOrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    private User organizer;
    private User buyer;
    private InPersonEvent event;

    @BeforeEach
    void setUp() {
        organizer = userRepository.save(user("organizer-" + UUID.randomUUID() + "@example.com"));
        buyer = userRepository.save(user("buyer-" + UUID.randomUUID() + "@example.com"));

        event = new InPersonEvent(
                "Conference", "Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("20.00"), "Address",
                new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);
        participationRepository.save(new EventParticipation(organizer, event, EventRole.ORGANIZER));
    }

    @Test
    void findPendingOrphans_ReturnsOnlyOldPendingOrdersWithoutPayment() {
        LocalDateTime old = LocalDateTime.now().minusMinutes(30);
        TicketOrder orphan = orderWithTickets(old, 2, TicketStatus.PENDING);
        TicketOrder paid = orderWithTickets(old, 1, TicketStatus.PENDING);
        paymentRepository.save(new Payment("111", new BigDecimal("20.00"), old, paid));
        orderWithTickets(LocalDateTime.now(), 1, TicketStatus.PENDING); // demasiado reciente
        orderWithTickets(old, 1, TicketStatus.CANCELLED); // ya resuelta
        entityManager.flush();

        List<TicketOrder> orphans = orderRepository.findPendingOrphans(
                TicketStatus.PENDING, LocalDateTime.now().minusMinutes(15));

        assertEquals(List.of(orphan.getId()), orphans.stream().map(TicketOrder::getId).toList());
    }

    @Test
    void cancelPendingWithoutPayment_CancelsEveryPendingTicketOfTheOrder() {
        TicketOrder order = orderWithTickets(LocalDateTime.now(), 3, TicketStatus.PENDING);
        entityManager.flush();

        int updated = ticketRepository.cancelPendingWithoutPayment(
                order, TicketStatus.PENDING, TicketStatus.CANCELLED);
        entityManager.clear();

        assertEquals(3, updated);
        assertTrue(ticketRepository.findByOrder(order).stream()
                .allMatch(ticket -> ticket.getStatus() == TicketStatus.CANCELLED));
    }

    @Test
    void cancelPendingWithoutPayment_WhenOrderHasPayment_DoesNothing() {
        TicketOrder order = orderWithTickets(LocalDateTime.now(), 2, TicketStatus.PENDING);
        paymentRepository.save(new Payment("222", new BigDecimal("40.00"), LocalDateTime.now(), order));
        entityManager.flush();

        int updated = ticketRepository.cancelPendingWithoutPayment(
                order, TicketStatus.PENDING, TicketStatus.CANCELLED);

        assertEquals(0, updated);
    }

    @Test
    void findByPaymentTransactionId_ReturnsTheEventOrganizerAccount() {
        OrganizerMpAccount account = mpAccountRepository.save(new OrganizerMpAccount(
                organizer, "token", "refresh", "123", "org@mp.com", LocalDateTime.now().plusDays(30)));
        TicketOrder order = orderWithTickets(LocalDateTime.now(), 2, TicketStatus.PENDING);
        paymentRepository.save(new Payment("333", new BigDecimal("40.00"), LocalDateTime.now(), order));
        entityManager.flush();

        assertEquals(account.getId(), mpAccountRepository
                .findByPaymentTransactionId("333", EventRole.ORGANIZER).orElseThrow().getId());
    }

    @Test
    void unlinkQueries_IgnoreFreeEvents() {
        InPersonEvent freeEvent = new InPersonEvent(
                "Free", "Description", LocalDateTime.now().plusDays(1),
                BigDecimal.ZERO, "Address",
                new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        freeEvent.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(freeEvent);
        participationRepository.save(new EventParticipation(organizer, freeEvent, EventRole.ORGANIZER));
        TicketOrder freeOrder = orderRepository.save(
                new TicketOrder(buyer, freeEvent, 1, BigDecimal.ZERO, LocalDateTime.now()));
        Ticket freeTicket = new Ticket(freeOrder);
        freeTicket.setStatus(TicketStatus.ACTIVE);
        ticketRepository.save(freeTicket);
        entityManager.flush();

        // Las entradas gratuitas activas no bloquean la desvinculación...
        assertFalse(ticketRepository.existsInPaidEventsByOrganizerAndStatusIn(
                organizer, EventRole.ORGANIZER, List.of(TicketStatus.PENDING, TicketStatus.ACTIVE)));
        // ...y el evento gratuito no vuelve a DRAFT; solo el de pago sin ventas.
        assertEquals(List.of(event.getId()), eventRepository.findPaidPublishedWithoutOpenTickets(
                organizer, EventRole.ORGANIZER, EventStatus.PUBLISHED,
                List.of(TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED))
                .stream().map(e -> e.getId()).toList());
    }

    @Test
    void countByEventInAndStatusIn_GroupsReservedTicketsByEvent() {
        InPersonEvent emptyEvent = new InPersonEvent(
                "Empty", "Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("20.00"), "Address",
                new BigDecimal("-34.60"), new BigDecimal("-58.38"), 50);
        eventRepository.save(emptyEvent);
        orderWithTickets(LocalDateTime.now(), 2, TicketStatus.PENDING);
        orderWithTickets(LocalDateTime.now(), 1, TicketStatus.USED);
        orderWithTickets(LocalDateTime.now(), 3, TicketStatus.CANCELLED); // no ocupa cupo
        entityManager.flush();

        List<EventTicketCount> counts = ticketRepository.countByEventInAndStatusIn(
                List.of(event, emptyEvent),
                List.of(TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED));

        // Los eventos sin entradas no aparecen: el servicio los trata como 0.
        assertEquals(List.of(new EventTicketCount(event.getId(), 3)), counts);
    }

    private TicketOrder orderWithTickets(LocalDateTime createdAt, int quantity, TicketStatus status) {
        TicketOrder order = orderRepository.save(
                new TicketOrder(buyer, event, quantity, event.getBasePrice(), createdAt));
        for (int i = 0; i < quantity; i++) {
            Ticket ticket = new Ticket(order);
            ticket.setStatus(status);
            ticketRepository.save(ticket);
        }
        return order;
    }

    private User user(String email) {
        return new User("Test User", email, "password", LocalDate.of(1990, 1, 1));
    }
}
