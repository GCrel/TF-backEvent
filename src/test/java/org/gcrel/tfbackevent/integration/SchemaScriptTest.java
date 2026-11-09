package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Reproduce el arranque de prod sobre una base vacía: db/schema.sql crea el esquema y Hibernate lo
// valida contra las entidades (ddl-auto=validate). Si alguien agrega una tabla o columna a una
// entidad y olvida el script, el contexto no arranca y este test falla.
@DataJpaTest(properties = { "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema.sql" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class SchemaScriptTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Test
    void schemaScript_OnAnEmptyDatabase_PassesHibernateValidation() {
        assertEquals(0, userRepository.count());
    }

    // Con mode=always en prod, Spring cargaría un data.sql de la raíz: no debe existir ninguno.
    @Test
    void thereIsNoDataSqlOnTheClasspath() {
        assertNull(getClass().getClassLoader().getResource("data.sql"));
    }
}
