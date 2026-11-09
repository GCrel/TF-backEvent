package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.integration.support.PostgresIntegrationTest;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.report.repository.ReportRepository;
import org.gcrel.tfbackevent.report.repository.dto.AttendeeRow;
import org.gcrel.tfbackevent.report.repository.dto.ControllerValidations;
import org.gcrel.tfbackevent.report.repository.dto.EventRevenue;
import org.gcrel.tfbackevent.report.repository.dto.PaymentStatusTotals;
import org.gcrel.tfbackevent.report.repository.dto.SaleRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;

// Verifica contra PostgreSQL las consultas de reportes con datos exactos: los mocks no validan
// agregados. Todas las aserciones comparan sumas, conteos y fechas conocidas.
//
// Datos (fechas de 2026):
//   E  presencial, 3 organizado por `organizer`
//      O1 2 entradas (100) pago APROBADO 10/3 23:59:30 -> 1 ACTIVE, 1 USED (acceso 20/3 09:15, controlador c1)
//      O2 1 entrada  (50)  pago APROBADO 11/3 00:00:00 -> 1 USED (acceso 09:45, c1)
//      O3 3 entradas (150) pago APROBADO 12/3 15:00    -> 1 USED (21:05, c2), 1 ACTIVE con acceso 09:50 sin
//                                                         controlador, 1 ACTIVE sin acceso
//      O4 1 entrada  (50)  pago RECHAZADO 12/3 16:00   -> 1 CANCELLED
//      O5 2 entradas (100) pago PENDIENTE 13/3 00:00   -> 2 PENDING
//      O6 2 entradas gratis, sin pago, creada 14/3 12:00 -> 2 ACTIVE
//   E2 virtual, organizado por `organizer`: O7 1 entrada (40) pago APROBADO 12/3 10:00, ACTIVE con acceso 12/3 11:00
//   E3 borrador de `organizer`, sin ventas
//   E4 presencial de `organizer2`: O8 1 entrada (25) pago APROBADO 12/3 11:00, ACTIVE
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportQueriesTest extends PostgresIntegrationTest {
    private static final LocalDateTime MAR_11 = LocalDateTime.of(2026, 3, 11, 0, 0);
    private static final LocalDateTime MAR_13 = LocalDateTime.of(2026, 3, 13, 0, 0);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventParticipationRepository participationRepository;
    @Autowired
    private TicketOrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ReportRepository reports;

    private User organizer;
    private User organizer2;
    private User c1;
    private User c2;
    private Event e;
    private Event e2;
    private Event e3;
    private Event e4;
    private int paymentSequence;

    @BeforeEach
    void setUp() {
        organizer = user("Organizador", null);
        organizer2 = user("Otro organizador", null);
        c1 = user("Controlador 1", null);
        c2 = user("Controlador 2", null);
        User b1 = user("Comprador 1", LocalDateTime.of(2026, 3, 10, 23, 59));
        User b2 = user("Comprador 2", LocalDateTime.of(2026, 3, 11, 8, 0));
        User b3 = user("Comprador 3", LocalDateTime.of(2026, 3, 12, 23, 59));
        User b4 = user("Comprador 4", LocalDateTime.of(2026, 3, 13, 0, 0));

        e = inPerson("E presencial", EventStatus.PUBLISHED);
        e2 = virtual("E2 virtual", EventStatus.PUBLISHED);
        e3 = inPerson("E3 borrador", EventStatus.DRAFT);
        e4 = inPerson("E4 de otro organizador", EventStatus.PUBLISHED);
        organizes(organizer, e, e2, e3);
        organizes(organizer2, e4);

        TicketOrder o1 = order(b1, e, 2, "50.00", LocalDateTime.of(2026, 3, 10, 23, 0));
        payment(o1, PaymentStatus.APPROVED, LocalDateTime.of(2026, 3, 10, 23, 59, 30));
        ticket(o1, TicketStatus.ACTIVE, null, null);
        ticket(o1, TicketStatus.USED, LocalDateTime.of(2026, 3, 20, 9, 15), c1);

        TicketOrder o2 = order(b2, e, 1, "50.00", LocalDateTime.of(2026, 3, 10, 23, 30));
        payment(o2, PaymentStatus.APPROVED, LocalDateTime.of(2026, 3, 11, 0, 0));
        ticket(o2, TicketStatus.USED, LocalDateTime.of(2026, 3, 20, 9, 45), c1);

        TicketOrder o3 = order(b3, e, 3, "50.00", LocalDateTime.of(2026, 3, 12, 14, 0));
        payment(o3, PaymentStatus.APPROVED, LocalDateTime.of(2026, 3, 12, 15, 0));
        ticket(o3, TicketStatus.USED, LocalDateTime.of(2026, 3, 20, 21, 5), c2);
        ticket(o3, TicketStatus.ACTIVE, LocalDateTime.of(2026, 3, 20, 9, 50), null);
        ticket(o3, TicketStatus.ACTIVE, null, null);

        TicketOrder o4 = order(b4, e, 1, "50.00", LocalDateTime.of(2026, 3, 12, 15, 30));
        payment(o4, PaymentStatus.REJECTED, LocalDateTime.of(2026, 3, 12, 16, 0));
        // Con acceso a propósito (dato inconsistente): demuestra que solo cuentan
        // ACTIVE y USED.
        ticket(o4, TicketStatus.CANCELLED, LocalDateTime.of(2026, 3, 20, 10, 0), null);

        TicketOrder o5 = order(b1, e, 2, "50.00", LocalDateTime.of(2026, 3, 12, 23, 50));
        payment(o5, PaymentStatus.PENDING, MAR_13);
        ticket(o5, TicketStatus.PENDING, null, null);
        ticket(o5, TicketStatus.PENDING, null, null);

        TicketOrder o6 = order(b2, e, 2, "0.00", LocalDateTime.of(2026, 3, 14, 12, 0));
        ticket(o6, TicketStatus.ACTIVE, null, null);
        ticket(o6, TicketStatus.ACTIVE, null, null);

        TicketOrder o7 = order(b1, e2, 1, "40.00", LocalDateTime.of(2026, 3, 12, 9, 50));
        payment(o7, PaymentStatus.APPROVED, LocalDateTime.of(2026, 3, 12, 10, 0));
        ticket(o7, TicketStatus.ACTIVE, LocalDateTime.of(2026, 3, 12, 11, 0), null);

        TicketOrder o8 = order(b2, e4, 1, "25.00", LocalDateTime.of(2026, 3, 12, 10, 50));
        payment(o8, PaymentStatus.APPROVED, LocalDateTime.of(2026, 3, 12, 11, 0));
        ticket(o8, TicketStatus.ACTIVE, null, null);

        entityManager.flush();
        entityManager.clear();
    }

    // ==========================================
    // Un evento
    // ==========================================

    @Test
    void ticketsByStatus_CountsEveryStatusOfThatEventOnly() {
        assertEquals(Map.of(TicketStatus.ACTIVE, 5L, TicketStatus.USED, 3L, TicketStatus.PENDING, 2L,
                TicketStatus.CANCELLED, 1L), ticketCounts(reports.ticketsByStatus(e.getId())));
        assertEquals(Map.of(TicketStatus.ACTIVE, 1L), ticketCounts(reports.ticketsByStatus(e2.getId())));
        assertEquals(Map.of(), ticketCounts(reports.ticketsByStatus(e3.getId())));
    }

    @Test
    void paymentsByStatus_SumsAmountsPerStatus() {
        List<PaymentStatusTotals> totals = reports.paymentsByStatus(e.getId());

        assertEquals(3, totals.size());
        assertTotals(totals, PaymentStatus.APPROVED, 3, "300.00");
        assertTotals(totals, PaymentStatus.REJECTED, 1, "50.00");
        assertTotals(totals, PaymentStatus.PENDING, 1, "100.00");
        assertEquals(List.of(), reports.paymentsByStatus(e3.getId()));
    }

    @Test
    void approvedSales_ReturnsOnePerApprovedPaymentWithItsDateQuantityAndAmount() {
        List<SaleRow> sales = reports.approvedSales(e.getId()).stream()
                .sorted(Comparator.comparing(SaleRow::date)).toList();

        assertEquals(3, sales.size());
        assertSale(sales.get(0), LocalDateTime.of(2026, 3, 10, 23, 59, 30), 2, "100.00");
        assertSale(sales.get(1), LocalDateTime.of(2026, 3, 11, 0, 0), 1, "50.00");
        assertSale(sales.get(2), LocalDateTime.of(2026, 3, 12, 15, 0), 3, "150.00");
    }

    @Test
    void freeSales_ReturnsFreeOrdersWithoutPaymentAndWithSoldTickets() {
        List<SaleRow> free = reports.freeSales(e.getId());

        assertEquals(1, free.size());
        assertSale(free.get(0), LocalDateTime.of(2026, 3, 14, 12, 0), 2, "0.00");
        assertEquals(List.of(), reports.freeSales(e2.getId()));
    }

    @Test
    void attendance_CountsSoldTicketsWithAccessAndIgnoresTheRest() {
        // 4: USED (3) y una ACTIVE con acceso. No cuentan la CANCELLED con acceso ni
        // las ACTIVE sin acceso.
        assertEquals(4, reports.attendance(e.getId()));
        assertEquals(1, reports.attendance(e2.getId()));
        assertEquals(0, reports.attendance(e3.getId()));
    }

    @Test
    void accessTimes_ReturnsEachAccessInstant() {
        assertEquals(List.of(
                LocalDateTime.of(2026, 3, 20, 9, 15),
                LocalDateTime.of(2026, 3, 20, 9, 45),
                LocalDateTime.of(2026, 3, 20, 9, 50),
                LocalDateTime.of(2026, 3, 20, 21, 5)),
                reports.accessTimes(e.getId()).stream().sorted().toList());
    }

    @Test
    void validationsByController_CountsOnlyUsedTicketsPerController() {
        Map<String, Long> byName = reports.validationsByController(e.getId()).stream()
                .collect(Collectors.toMap(ControllerValidations::name, ControllerValidations::count));

        assertEquals(Map.of("Controlador 1", 2L, "Controlador 2", 1L), byName);
        assertEquals(List.of(), reports.validationsByController(e2.getId()));
    }

    @Test
    void attendees_ListsOnlySoldTicketsOldestPurchaseFirstAndPaginates() {
        Page<AttendeeRow> all = reports.attendees(e.getId(), PageRequest.of(0, 20));

        // 8 vendidas (5 ACTIVE + 3 USED): quedan fuera las 2 PENDING y la CANCELLED.
        assertEquals(8, all.getTotalElements());
        assertEquals(List.of("Comprador 1", "Comprador 1", "Comprador 2", "Comprador 3", "Comprador 3",
                "Comprador 3", "Comprador 2", "Comprador 2"),
                all.map(AttendeeRow::name).getContent());
        assertEquals(LocalDateTime.of(2026, 3, 10, 23, 0), all.getContent().get(0).boughtAt());
        assertEquals(List.of(TicketStatus.ACTIVE, TicketStatus.USED), all.getContent().subList(0, 2).stream()
                .map(AttendeeRow::status).sorted().toList());

        Page<AttendeeRow> second = reports.attendees(e.getId(), PageRequest.of(1, 3));
        assertEquals(3, second.getNumberOfElements());
        assertEquals(3, second.getTotalPages());
        assertEquals(List.of("Comprador 3", "Comprador 3", "Comprador 3"), second.map(AttendeeRow::name).getContent());
        assertEquals(List.of(), reports.attendees(e3.getId(), PageRequest.of(0, 20)).getContent());
    }

    // ==========================================
    // Un conjunto de eventos
    // ==========================================

    @Test
    void aggregatesOverASetOfEvents_ExcludeOtherEvents() {
        Set<UUID> ids = Set.of(e.getId(), e2.getId(), e3.getId());

        assertEquals(Map.of(TicketStatus.ACTIVE, 6L, TicketStatus.USED, 3L, TicketStatus.PENDING, 2L,
                TicketStatus.CANCELLED, 1L), ticketCounts(reports.ticketsByStatusIn(ids)));
        List<PaymentStatusTotals> payments = reports.paymentsByStatusIn(ids);
        assertTotals(payments, PaymentStatus.APPROVED, 4, "340.00");
        assertTotals(payments, PaymentStatus.REJECTED, 1, "50.00");
        assertTotals(payments, PaymentStatus.PENDING, 1, "100.00");
        assertEquals(Map.of(EventStatus.PUBLISHED, 2L, EventStatus.DRAFT, 1L),
                reports.eventsByStatusIn(ids).stream().collect(
                        Collectors.toMap(c -> c.status(), c -> c.count())));
    }

    @Test
    void topEventsByRevenueIn_OrdersByRevenueAndRespectsTheLimit() {
        Set<UUID> ids = Set.of(e.getId(), e2.getId(), e3.getId());

        List<EventRevenue> top = reports.topEventsByRevenueIn(ids, PageRequest.of(0, 5));

        assertEquals(List.of(e.getId(), e2.getId()), top.stream().map(EventRevenue::eventId).toList());
        assertMoney("300.00", top.get(0).revenue());
        assertEquals(6, top.get(0).tickets());
        assertMoney("40.00", top.get(1).revenue());
        assertEquals(1, top.get(1).tickets());
        assertEquals(List.of(e.getId()),
                reports.topEventsByRevenueIn(ids, PageRequest.of(0, 1)).stream().map(EventRevenue::eventId).toList());
    }

    @Test
    void organizedEventIds_ReturnsOnlyTheEventsTheUserOrganizes() {
        assertEquals(Set.of(e.getId(), e2.getId(), e3.getId()),
                Set.copyOf(reports.organizedEventIds(organizer.getId())));
        assertEquals(List.of(e4.getId()), reports.organizedEventIds(organizer2.getId()));
        assertEquals(List.of(), reports.organizedEventIds(c1.getId()));
    }

    // ==========================================
    // Global, por período [11/3, 13/3)
    // ==========================================

    @Test
    void paymentsByStatusBetween_IncludesTheLowerBoundAndExcludesTheUpperOne() {
        List<PaymentStatusTotals> totals = reports.paymentsByStatusBetween(MAR_11, MAR_13);

        // APROBADOS: O2 (11/3 00:00, límite inferior incluido), O3, O7, O8; no O1 (10/3
        // 23:59:30).
        assertTotals(totals, PaymentStatus.APPROVED, 4, "265.00");
        assertTotals(totals, PaymentStatus.REJECTED, 1, "50.00");
        // El pendiente O5 cae justo en el límite superior (13/3 00:00): queda fuera.
        assertEquals(2, totals.size());
    }

    @Test
    void ticketsSoldBetween_AddsPaidAndFreeOrders() {
        assertEquals(6, reports.paidTicketsBetween(MAR_11, MAR_13));
        assertEquals(0, reports.freeTicketsBetween(MAR_11, MAR_13));
        assertEquals(2, reports.freeTicketsBetween(
                LocalDateTime.of(2026, 3, 14, 0, 0), LocalDateTime.of(2026, 3, 15, 0, 0)));
        assertEquals(0, reports.paidTicketsBetween(
                LocalDateTime.of(2026, 4, 1, 0, 0), LocalDateTime.of(2026, 4, 2, 0, 0)));
    }

    @Test
    void topEventsByRevenueBetween_RanksEventsByTheRevenueOfThePeriod() {
        List<EventRevenue> top = reports.topEventsByRevenueBetween(MAR_11, MAR_13, PageRequest.of(0, 10));

        assertEquals(List.of(e.getId(), e2.getId(), e4.getId()), top.stream().map(EventRevenue::eventId).toList());
        assertMoney("200.00", top.get(0).revenue());
        assertEquals(4, top.get(0).tickets());
        assertMoney("40.00", top.get(1).revenue());
        assertMoney("25.00", top.get(2).revenue());
    }

    @Test
    void eventsByStatus_CountsAllEvents() {
        assertEquals(Map.of(EventStatus.PUBLISHED, 3L, EventStatus.DRAFT, 1L),
                reports.eventsByStatus().stream().collect(Collectors.toMap(c -> c.status(), c -> c.count())));
    }

    @Test
    void countUsers_SeparatesNewUsersByCreationDate() {
        assertEquals(8, reports.countUsers());
        // b2 (11/3 08:00) y b3 (12/3 23:59). b4 (13/3 00:00) queda fuera por el límite
        // superior; b1 (10/3
        // 23:59) por el inferior; los usuarios sin fecha no cuentan.
        assertEquals(2, reports.countUsersCreatedBetween(MAR_11, MAR_13));
    }

    // ==========================================
    // Auxiliares
    // ==========================================

    private static Map<TicketStatus, Long> ticketCounts(
            List<org.gcrel.tfbackevent.report.repository.dto.TicketStatusCount> counts) {
        return counts.stream().collect(Collectors.toMap(c -> c.status(), c -> c.count()));
    }

    private static void assertTotals(List<PaymentStatusTotals> totals, PaymentStatus status, long count,
            String amount) {
        PaymentStatusTotals row = totals.stream().filter(t -> t.status() == status).findFirst().orElseThrow();
        assertEquals(count, row.count(), "cantidad de " + status);
        assertMoney(amount, row.total());
    }

    private static void assertSale(SaleRow sale, LocalDateTime date, int tickets, String amount) {
        assertEquals(date, sale.date());
        assertEquals(tickets, sale.tickets());
        assertMoney(amount, sale.amount());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0, "esperado " + expected + " pero fue " + actual);
    }

    private User user(String name, LocalDateTime createdAt) {
        User user = new User(name, name.toLowerCase().replace(' ', '.') + "@example.com", "hash",
                LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        return userRepository.save(user);
    }

    private Event inPerson(String title, EventStatus status) {
        InPersonEvent event = new InPersonEvent(title, "Descripción", LocalDateTime.of(2026, 3, 20, 18, 0),
                new BigDecimal("50.00"), "Dirección", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        event.setStatus(status);
        return eventRepository.save(event);
    }

    private Event virtual(String title, EventStatus status) {
        VirtualEvent event = new VirtualEvent(title, "Descripción", LocalDateTime.of(2026, 3, 20, 18, 0),
                new BigDecimal("40.00"), "https://meet.example.com", "Meet", null);
        event.setStatus(status);
        return eventRepository.save(event);
    }

    private void organizes(User user, Event... events) {
        for (Event event : events) {
            participationRepository.save(new EventParticipation(user, event, EventRole.ORGANIZER));
        }
    }

    private TicketOrder order(User buyer, Event event, int quantity, String unitPrice, LocalDateTime createdAt) {
        return orderRepository.save(new TicketOrder(buyer, event, quantity, new BigDecimal(unitPrice), createdAt));
    }

    private void payment(TicketOrder order, PaymentStatus status, LocalDateTime date) {
        Payment payment = new Payment("tx-" + (++paymentSequence), order.getTotalAmount(), date, order);
        payment.setPaymentStatus(status);
        paymentRepository.save(payment);
    }

    private void ticket(TicketOrder order, TicketStatus status, LocalDateTime accessedAt, User validatedBy) {
        Ticket ticket = new Ticket(order);
        ticket.setStatus(status);
        ticket.setAccessedAt(accessedAt);
        ticket.setValidatedBy(validatedBy);
        ticketRepository.save(ticket);
    }
}
