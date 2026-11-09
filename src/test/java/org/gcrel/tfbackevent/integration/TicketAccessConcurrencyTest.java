package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicketAccessConcurrencyTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventParticipationRepository participationRepository;

    @Autowired
    private TicketOrderRepository orderRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAllById(createdTicketIds);
        orderRepository.deleteAllById(createdOrderIds);
        participationRepository.deleteAll();
        eventRepository.deleteAllById(createdEventIds);
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void markAsUsedIfActive_WhenScannedConcurrently_SucceedsExactlyOnce() throws Exception {
        Fixture fixture = persistActiveTicket();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return ticketRepository.markAsUsedIfActive(
                            fixture.ticket().getId(),
                            LocalDateTime.now(),
                            fixture.controller(),
                            TicketStatus.ACTIVE,
                            TicketStatus.USED);
                }));
            }
            start.countDown();

            int total = 0;
            for (Future<Integer> result : results) {
                total += result.get();
            }
            assertEquals(1, total);

            Ticket used = ticketRepository.findById(fixture.ticket().getId()).orElseThrow();
            assertEquals(TicketStatus.USED, used.getStatus());
            assertNotNull(used.getAccessedAt());
            assertEquals(fixture.controller().getId(), used.getValidatedBy().getId());
            assertEquals(fixture.controller().getName(),
                    ticketRepository.findAccessStateById(used.getId()).orElseThrow().validatedByName());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void markFirstAccess_WhenCalledConcurrently_SucceedsExactlyOnce() throws Exception {
        Fixture fixture = persistActiveTicket();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return ticketRepository.markFirstAccess(
                            fixture.ticket().getId(), LocalDateTime.now());
                }));
            }
            start.countDown();

            int total = 0;
            for (Future<Integer> result : results) {
                total += result.get();
            }
            assertEquals(1, total);
            assertNotNull(ticketRepository.findById(fixture.ticket().getId()).orElseThrow().getAccessedAt());
        } finally {
            pool.shutdownNow();
        }
    }

    private Fixture persistActiveTicket() {
        User attendee = user("attendee-" + UUID.randomUUID() + "@example.com");
        User controller = user("controller-" + UUID.randomUUID() + "@example.com");
        userRepository.saveAll(List.of(attendee, controller));

        InPersonEvent event = new InPersonEvent(
                "Conference", "Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("20.00"), "Address",
                new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        event.setStatus(EventStatus.PUBLISHED);
        Event savedEvent = eventRepository.save(event);
        createdEventIds.add(savedEvent.getId());
        participationRepository.save(new EventParticipation(controller, event, EventRole.CONTROLLER));

        TicketOrder order = orderRepository.save(
                new TicketOrder(attendee, savedEvent, 1, savedEvent.getBasePrice(), LocalDateTime.now()));
        createdOrderIds.add(order.getId());
        Ticket ticket = new Ticket(order);
        ticket.setStatus(TicketStatus.ACTIVE);
        Ticket savedTicket = ticketRepository.saveAndFlush(ticket);
        createdTicketIds.add(savedTicket.getId());
        return new Fixture(savedTicket, controller);
    }

    private User user(String email) {
        User user = new User("Test User", email, "password", LocalDate.of(1990, 1, 1));
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private record Fixture(Ticket ticket, User controller) {
    }
}
