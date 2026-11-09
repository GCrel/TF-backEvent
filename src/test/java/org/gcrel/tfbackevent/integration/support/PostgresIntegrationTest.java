package org.gcrel.tfbackevent.integration.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Base de las pruebas contra PostgreSQL real: un único contenedor para todas las clases que la
// extienden (se arranca una vez y lo cierra Testcontainers al terminar la JVM). Cada subclase
// declara su propia anotación de prueba (@DataJpaTest, etc.). Sin Docker, las pruebas se omiten.
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }
}
