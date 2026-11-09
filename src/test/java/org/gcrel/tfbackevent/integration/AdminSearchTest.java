package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.dto.response.AuditLogResponse;
import org.gcrel.tfbackevent.admin.dto.response.UserAdminResponse;
import org.gcrel.tfbackevent.admin.service.AdminUserService;
import org.gcrel.tfbackevent.admin.service.AuditService;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.payment.service.PaymentRecoveryService;
import org.gcrel.tfbackevent.payment.service.DeliveryService;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.event.service.SectorService;
import org.gcrel.tfbackevent.event.service.ImageStorageService;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.config.EventWindowProperties;
import org.gcrel.tfbackevent.admin.service.AdminSupportService;
import org.gcrel.tfbackevent.admin.dto.response.TicketAdminResponse;
import org.gcrel.tfbackevent.admin.dto.response.PaymentAdminResponse;
import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.EntityManager;

// Verifica contra PostgreSQL las búsquedas del panel de administración.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({ AdminUserService.class, AuditService.class, AdminSupportService.class, EventService.class })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AdminSearchTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private AuditService auditService;

    @Autowired
    private AdminSupportService adminSupportService;
    @Autowired
    private EventService eventService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketOrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private PaymentRecoveryService paymentRecoveryService;
    @MockitoBean
    private DeliveryService deliveryService;
    @MockitoBean
    private EventWindowProperties eventWindowProperties;
    @MockitoBean
    private ImageStorageService imageStorageService;
    @MockitoBean
    private SectorService sectorService;

    private User ana;
    private User bruno;
    private User carla;

    @BeforeEach
    void setUp() {
        ana = save("Ana García", "ana@example.com", SystemRole.ADMIN);
        bruno = save("Bruno 100%", "bruno@example.com", SystemRole.USER);
        carla = save("Carla Méndez", "carla@example.com", SystemRole.USER);
        carla.block("Spam", java.time.LocalDateTime.now());
        entityManager.flush();
    }

    private User save(String name, String email, SystemRole role) {
        User user = new User(name, email, "hash", LocalDate.of(1990, 1, 1));
        user.setEnabled(true);
        user.setSystemRole(role);
        return userRepository.save(user);
    }

    private List<String> names(String q, SystemRole role, Boolean blocked) {
        return adminUserService.search(q, role, blocked, PageRequest.of(0, 20)).map(UserAdminResponse::name).getContent();
    }

    @Test
    void searchUsers_WithoutFilters_ReturnsEveryoneSortedByName() {
        assertEquals(List.of("Ana García", "Bruno 100%", "Carla Méndez"), names(null, null, null));
    }

    @Test
    void searchUsers_MatchesNameOrEmailIgnoringCase() {
        assertEquals(List.of("Ana García"), names("GARC", null, null));
        assertEquals(List.of("Carla Méndez"), names("carla@", null, null));
    }

    @Test
    void searchUsers_TreatsLikeWildcardsLiterally() {
        assertEquals(List.of("Bruno 100%"), names("100%", null, null));
        assertEquals(List.of(), names("%", SystemRole.ADMIN, null));
        assertEquals(List.of(), names("a_a", null, null));
    }

    @Test
    void searchUsers_FiltersByRoleAndBlocked() {
        assertEquals(List.of("Ana García"), names(null, SystemRole.ADMIN, null));
        assertEquals(List.of("Carla Méndez"), names(null, null, true));
        assertEquals(List.of("Ana García", "Bruno 100%"), names(null, null, false));
        assertEquals(List.of("Carla Méndez"), names(null, SystemRole.USER, true));
    }

    @Test
    void searchUsers_Paginates() {
        var page = adminUserService.search(null, null, null, PageRequest.of(1, 2));
        assertEquals(3, page.getTotalElements());
        assertEquals(List.of("Carla Méndez"), page.map(UserAdminResponse::name).getContent());
    }

    @Test
    void searchAudit_FiltersAndReturnsNewestFirst() {
        UUID target = bruno.getId();
        auditService.record(ana, AuditAction.USER_BLOCKED, AuditTargetType.USER, target, "primero");
        auditService.record(ana, AuditAction.USER_UNBLOCKED, AuditTargetType.USER, target, "segundo");
        auditService.record(ana, AuditAction.USER_BLOCKED, AuditTargetType.USER, carla.getId(), "otro");
        entityManager.flush();
        entityManager.clear();
        PageRequest page = PageRequest.of(0, 20);

        List<String> all = auditService.search(null, null, null, null, null, page)
                .map(AuditLogResponse::details).getContent();
        assertEquals(3, all.size());
        assertEquals("otro", all.get(0));

        assertEquals(2, auditService.search(ana.getId(), null, target, null, null, page).getTotalElements());
        assertEquals(1, auditService.search(null, AuditAction.USER_UNBLOCKED, null, null, null, page).getTotalElements());
        assertEquals(0, auditService.search(bruno.getId(), null, null, null, null, page).getTotalElements());
        assertEquals(3, auditService.search(null, null, null, LocalDate.now(), LocalDate.now(), page).getTotalElements());
        assertEquals(0, auditService.search(null, null, null, LocalDate.now().plusDays(1), null, page).getTotalElements());
        assertEquals("Ana García", auditService.search(null, null, null, null, null, page).getContent().get(0).actorName());
    }

    // ==========================================
    // Eventos, pagos y tickets
    // ==========================================

    private InPersonEvent inPerson(String title, EventStatus status, int daysFromNow) {
        InPersonEvent event = new InPersonEvent(title, "Descripción de " + title, LocalDateTime.now().plusDays(daysFromNow),
                new BigDecimal("20.00"), "Address", new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        event.setStatus(status);
        return eventRepository.save(event);
    }

    private VirtualEvent virtual(String title, EventStatus status, int daysFromNow) {
        VirtualEvent event = new VirtualEvent(title, "Descripción de " + title, LocalDateTime.now().plusDays(daysFromNow),
                new BigDecimal("20.00"), "https://meet.example.com", "Meet", null);
        event.setStatus(status);
        return eventRepository.save(event);
    }

    private List<String> titles(EventStatus status, EventType type, String q) {
        return eventService.getAllEvents(status, type, q, PageRequest.of(0, 20)).map(EventResponse::title).getContent();
    }

    @Test
    void getAllEvents_ReturnsEveryStatusNewestFirstAndFilters() {
        inPerson("Conf draft", EventStatus.DRAFT, 1);
        inPerson("Conf cancelled", EventStatus.CANCELLED, 5);
        virtual("Webinar published", EventStatus.PUBLISHED, 3);
        entityManager.flush();

        assertEquals(List.of("Conf cancelled", "Webinar published", "Conf draft"), titles(null, null, null));
        assertEquals(List.of("Conf draft"), titles(EventStatus.DRAFT, null, null));
        assertEquals(List.of("Webinar published"), titles(null, EventType.VIRTUAL, null));
        assertEquals(List.of("Conf cancelled", "Conf draft"), titles(null, EventType.IN_PERSON, "CONF"));
        assertEquals(List.of(), titles(EventStatus.FINISHED, null, null));
        assertEquals(List.of("Webinar published"), titles(null, null, "descripción de web"));
    }

    private TicketOrder orderWithTicket(User orderBuyer, InPersonEvent orderEvent, TicketStatus ticketStatus) {
        TicketOrder order = orderRepository.save(
                new TicketOrder(orderBuyer, orderEvent, 1, orderEvent.getBasePrice(), LocalDateTime.now()));
        Ticket ticket = new Ticket(order);
        ticket.setStatus(ticketStatus);
        ticketRepository.save(ticket);
        return order;
    }

    @Test
    void searchPaymentsAndTickets_FilterByUserEventAndStatus() {
        InPersonEvent first = inPerson("Primero", EventStatus.PUBLISHED, 1);
        InPersonEvent second = inPerson("Segundo", EventStatus.PUBLISHED, 2);
        TicketOrder brunoFirst = orderWithTicket(bruno, first, TicketStatus.ACTIVE);
        TicketOrder brunoSecond = orderWithTicket(bruno, second, TicketStatus.PENDING);
        TicketOrder carlaFirst = orderWithTicket(carla, first, TicketStatus.CANCELLED);
        Payment approved = new Payment("tx-1", new BigDecimal("20.00"), LocalDateTime.now().minusHours(2), brunoFirst);
        approved.setPaymentStatus(PaymentStatus.APPROVED);
        paymentRepository.save(approved);
        paymentRepository.save(new Payment("tx-2", new BigDecimal("20.00"), LocalDateTime.now().minusHours(1), brunoSecond));
        paymentRepository.save(new Payment("tx-3", new BigDecimal("20.00"), LocalDateTime.now(), carlaFirst));
        entityManager.flush();
        entityManager.clear();
        PageRequest page = PageRequest.of(0, 20);

        assertEquals(List.of("tx-3", "tx-2", "tx-1"), adminSupportService.searchPayments(null, null, null, page)
                .map(PaymentAdminResponse::transactionId).getContent());
        assertEquals(List.of("tx-2", "tx-1"), adminSupportService.searchPayments(bruno.getId(), null, null, page)
                .map(PaymentAdminResponse::transactionId).getContent());
        assertEquals(List.of("tx-3", "tx-1"), adminSupportService.searchPayments(null, first.getId(), null, page)
                .map(PaymentAdminResponse::transactionId).getContent());
        assertEquals(List.of("tx-1"), adminSupportService.searchPayments(null, null, PaymentStatus.APPROVED, page)
                .map(PaymentAdminResponse::transactionId).getContent());
        PaymentAdminResponse row = adminSupportService.searchPayments(carla.getId(), null, null, page).getContent().get(0);
        assertEquals("Primero", row.eventTitle());
        assertEquals(carla.getName(), row.buyerName());

        assertEquals(3, adminSupportService.searchTickets(null, null, null, page).getTotalElements());
        assertEquals(2, adminSupportService.searchTickets(bruno.getId(), null, null, page).getTotalElements());
        assertEquals(2, adminSupportService.searchTickets(null, first.getId(), null, page).getTotalElements());
        List<TicketAdminResponse> pending = adminSupportService.searchTickets(null, null, TicketStatus.PENDING, page).getContent();
        assertEquals(1, pending.size());
        assertEquals("Segundo", pending.get(0).eventTitle());
        assertEquals(bruno.getName(), pending.get(0).attendeeName());
    }
}
