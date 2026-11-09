package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.dto.response.AuditLogResponse;
import org.gcrel.tfbackevent.admin.dto.response.UserAdminResponse;
import org.gcrel.tfbackevent.admin.service.AdminUserService;
import org.gcrel.tfbackevent.admin.service.AuditService;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
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
@Import({ AdminUserService.class, AuditService.class })
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

    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private EmailService emailService;

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
}
