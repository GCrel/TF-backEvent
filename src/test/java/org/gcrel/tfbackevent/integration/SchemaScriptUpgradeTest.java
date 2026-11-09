package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// db/schema.sql se ejecuta en cada arranque: debe poder repetirse y debe completar una base creada
// con una versión anterior sin perder sus datos.
@Testcontainers(disabledWithoutDocker = true)
class SchemaScriptUpgradeTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static DataSource dataSource;

    @BeforeAll
    static void connect() {
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void runSchemaScript() {
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
    }

    private static void sql(String statement) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute(statement);
        }
    }

    private static String query(String statement) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(statement)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    void runningTheScriptTwiceDoesNotFail() {
        runSchemaScript();
        runSchemaScript();
    }

    @Test
    void anOlderSchemaIsCompletedWithoutLosingData() throws SQLException {
        sql("DROP SCHEMA public CASCADE");
        sql("CREATE SCHEMA public");
        // Esquema de una versión anterior: sin columnas de bloqueo, imágenes, sectores ni
        // coordenadas con decimales, y sin las tablas event_sectors y audit_logs.
        sql("""
                CREATE TABLE users (id uuid PRIMARY KEY, name varchar(255) NOT NULL, email varchar(255) NOT NULL UNIQUE,
                    password varchar(255) NOT NULL, system_role varchar(255) NOT NULL, date_birth date NOT NULL,
                    is_enabled boolean NOT NULL, verification_code varchar(255),
                    verification_code_expires_at timestamp(6), verification_code_failed_attempts integer NOT NULL)""");
        sql("""
                CREATE TABLE events (id uuid PRIMARY KEY, title varchar(255) NOT NULL, description TEXT,
                    date_time timestamp(6) NOT NULL, status varchar(255) NOT NULL, base_price numeric(38,2) NOT NULL)""");
        sql("""
                CREATE TABLE in_person_events (id uuid PRIMARY KEY REFERENCES events, address varchar(255) NOT NULL,
                    latitude numeric(10,0), longitude numeric(10,0), capacity integer NOT NULL)""");
        sql("""
                CREATE TABLE ticket_orders (id uuid PRIMARY KEY, buyer_id uuid NOT NULL REFERENCES users,
                    event_id uuid NOT NULL REFERENCES events, quantity integer NOT NULL, unit_price numeric(38,2) NOT NULL,
                    total_amount numeric(38,2) NOT NULL, created_at timestamp(6) NOT NULL,
                    delivery_status varchar(255) NOT NULL, delivery_attempts integer NOT NULL,
                    next_delivery_attempt_at timestamp(6), last_delivery_error varchar(255), version bigint NOT NULL)""");
        sql("INSERT INTO users VALUES ('11111111-1111-1111-1111-111111111111', 'Ana', 'ana@example.com', 'hash', 'ADMIN',"
                + " DATE '1990-01-01', true, null, null, 0)");

        runSchemaScript();

        assertEquals("1", query("SELECT count(*) FROM users"));
        assertEquals("f", query("SELECT blocked FROM users"));
        assertEquals("6", query("SELECT count(*) FROM information_schema.columns WHERE table_name = 'users'"
                + " AND column_name IN ('blocked', 'blocked_reason', 'blocked_at', 'code_purpose', 'pending_email',"
                + " 'avatar_key')"));
        assertEquals("1", query("SELECT count(*) FROM information_schema.columns WHERE table_name = 'events'"
                + " AND column_name = 'image_key'"));
        assertEquals("1", query("SELECT count(*) FROM information_schema.columns WHERE table_name = 'in_person_events'"
                + " AND column_name = 'layout_image_key'"));
        assertEquals("1", query("SELECT count(*) FROM information_schema.columns WHERE table_name = 'ticket_orders'"
                + " AND column_name = 'sector_id'"));
        assertEquals("7", query("SELECT numeric_scale FROM information_schema.columns WHERE table_name = 'in_person_events'"
                + " AND column_name = 'latitude'"));
        assertEquals("2", query("SELECT count(*) FROM information_schema.tables WHERE table_name"
                + " IN ('audit_logs', 'event_sectors')"));
    }
}
