package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.gcrel.tfbackevent.admin.repository.AuditLogRepository;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Carga data.sql sobre el esquema que genera Hibernate, como en el perfil dev: detecta columnas
// nuevas que el script no cubre.
@DataJpaTest(properties = { "spring.jpa.hibernate.ddl-auto=create", "spring.sql.init.mode=always",
        "spring.jpa.defer-datasource-initialization=true" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DevSeedDataTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void dataSql_LoadsWithABlockedUserAndAuditEntries() {
        User elena = userRepository.findById(UUID.fromString("55555555-5555-5555-5555-555555555555")).orElseThrow();
        assertTrue(elena.isBlocked());
        assertEquals(SystemRole.USER, elena.getSystemRole());
        assertEquals(2, auditLogRepository.count());
        // Los demás usuarios no quedan bloqueados por el valor por defecto de la columna.
        assertEquals(false, userRepository.findByEmail("ana.garcia@example.com").orElseThrow().isBlocked());
        assertEquals(SystemRole.ADMIN, userRepository.findByEmail("ana.garcia@example.com").orElseThrow().getSystemRole());
    }
}
