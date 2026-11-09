package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.gcrel.tfbackevent.admin.repository.AuditLogRepository;
import org.gcrel.tfbackevent.admin.service.AdminUserService;
import org.gcrel.tfbackevent.admin.service.AuditService;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Cada hilo abre su propia transacción: verifica contra PostgreSQL que el bloqueo de las filas de
// superusuario serializa los cambios de rol.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({ AdminUserService.class, AuditService.class })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminUserConcurrencyTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private EmailService emailService;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void twoAdminsDemotingEachOtherAtOnce_LeaveExactlyOneAdmin() throws Exception {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        User first = admin("first");
        User second = admin("second");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> a = pool.submit(demote(start, first, second));
            Future<Boolean> b = pool.submit(demote(start, second, first));
            start.countDown();

            int successes = (a.get() ? 1 : 0) + (b.get() ? 1 : 0);

            assertEquals(1, successes);
            long admins = userRepository.findAllById(createdUserIds).stream()
                    .filter(user -> user.getSystemRole() == SystemRole.ADMIN)
                    .count();
            assertEquals(1, admins);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Boolean> demote(CountDownLatch start, User actor, User target) {
        return () -> {
            start.await();
            try {
                adminUserService.changeRole(actor, target.getId(), SystemRole.USER, "irrelevante");
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        };
    }

    private User admin(String prefix) {
        User user = new User("Admin " + prefix, prefix + "-" + UUID.randomUUID() + "@example.com", "hash",
                LocalDate.of(1990, 1, 1));
        user.setEnabled(true);
        user.setSystemRole(SystemRole.ADMIN);
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }
}
