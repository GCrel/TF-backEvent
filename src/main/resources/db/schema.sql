-- =====================================================================
-- db/schema.sql - Esquema de la base para el perfil prod
--
-- Se ejecuta en cada arranque, ANTES de que Hibernate valide el esquema
-- (ddl-auto=validate), así que debe ser idempotente: se puede correr sobre una
-- base vacía o sobre una existente sin romper nada.
--
--   Parte 1: CREATE TABLE IF NOT EXISTS de todas las tablas (base vacía, o tablas
--            nuevas en una base existente).
--   Parte 2: ADD COLUMN IF NOT EXISTS de las columnas que se agregaron después
--            (bases creadas con una versión anterior).
--   Parte 3: índices (CREATE INDEX IF NOT EXISTS).
--
-- Cómo mantenerlo: al agregar una tabla o columna a una entidad, agregala acá
-- (tabla en la parte 1, columna en la parte 2). SchemaScriptTest falla si falta.
-- Las restricciones CHECK de los enums no se replican: Java ya valida el enum y un
-- valor nuevo no debe exigir modificar la base.
-- Para regenerar la referencia exacta de Hibernate:
--   jakarta.persistence.schema-generation.scripts.action=create
--   jakarta.persistence.schema-generation.scripts.create-target=<archivo>
-- =====================================================================

-- ---------------------------------------------------------------------
-- Parte 1: tablas, en orden de dependencias
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id uuid NOT NULL,
    name varchar(255) NOT NULL,
    email varchar(255) NOT NULL UNIQUE,
    password varchar(255) NOT NULL,
    system_role varchar(255) NOT NULL,
    date_birth date NOT NULL,
    is_enabled boolean NOT NULL,
    verification_code varchar(255),
    verification_code_expires_at timestamp(6),
    verification_code_failed_attempts integer NOT NULL,
    code_purpose varchar(255),
    pending_email varchar(255),
    avatar_key varchar(255),
    blocked boolean DEFAULT false NOT NULL,
    blocked_reason varchar(500),
    blocked_at timestamp(6),
    created_at timestamp(6),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS events (
    id uuid NOT NULL,
    title varchar(255) NOT NULL,
    description TEXT,
    date_time timestamp(6) NOT NULL,
    status varchar(255) NOT NULL,
    base_price numeric(38,2) NOT NULL,
    image_key varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS in_person_events (
    id uuid NOT NULL REFERENCES events,
    address varchar(255) NOT NULL,
    latitude numeric(10,7),
    longitude numeric(10,7),
    capacity integer NOT NULL,
    layout_image_key varchar(255),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS virtual_events (
    id uuid NOT NULL REFERENCES events,
    platform varchar(255) NOT NULL,
    access_link varchar(255),
    access_instructions TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS event_sectors (
    id uuid NOT NULL,
    event_id uuid NOT NULL REFERENCES in_person_events,
    name varchar(50) NOT NULL,
    price numeric(10,2) NOT NULL,
    capacity integer NOT NULL,
    color varchar(7),
    points jsonb,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS event_participations (
    event_id uuid NOT NULL REFERENCES events,
    user_id uuid NOT NULL REFERENCES users,
    event_role varchar(255) NOT NULL,
    PRIMARY KEY (event_id, user_id, event_role)
);

CREATE TABLE IF NOT EXISTS refresh_token (
    id uuid NOT NULL,
    token varchar(255) NOT NULL UNIQUE,
    expires_at timestamp(6) NOT NULL,
    user_id uuid NOT NULL REFERENCES users,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS mp_oauth_states (
    state varchar(64) NOT NULL,
    user_id uuid NOT NULL REFERENCES users,
    expires_at timestamp(6) NOT NULL,
    PRIMARY KEY (state)
);

CREATE TABLE IF NOT EXISTS organizer_mp_accounts (
    id uuid NOT NULL,
    user_id uuid NOT NULL UNIQUE REFERENCES users,
    mp_access_token varchar(255) NOT NULL,
    mp_user_id varchar(255) NOT NULL,
    mp_email varchar(255) NOT NULL,
    linked_at timestamp(6) NOT NULL,
    mp_refresh_token varchar(255) NOT NULL,
    mp_token_expires_at timestamp(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ticket_orders (
    id uuid NOT NULL,
    buyer_id uuid NOT NULL REFERENCES users,
    event_id uuid NOT NULL REFERENCES events,
    sector_id uuid REFERENCES event_sectors,
    quantity integer NOT NULL,
    unit_price numeric(38,2) NOT NULL,
    total_amount numeric(38,2) NOT NULL,
    created_at timestamp(6) NOT NULL,
    delivery_status varchar(255) NOT NULL,
    delivery_attempts integer NOT NULL,
    next_delivery_attempt_at timestamp(6),
    last_delivery_error varchar(255),
    version bigint NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS payments (
    id uuid NOT NULL,
    transaction_id varchar(255) NOT NULL UNIQUE,
    total_amount numeric(38,2) NOT NULL,
    payment_date timestamp(6) NOT NULL,
    payment_status varchar(255) NOT NULL,
    mp_status varchar(255),
    status_detail varchar(255),
    next_reconcile_at timestamp(6),
    version bigint NOT NULL,
    order_id uuid NOT NULL UNIQUE REFERENCES ticket_orders,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS tickets (
    id uuid NOT NULL,
    bought_at timestamp(6) NOT NULL,
    status varchar(255) NOT NULL,
    accessed_at timestamp(6),
    attendee_id uuid NOT NULL REFERENCES users,
    event_id uuid NOT NULL REFERENCES events,
    order_id uuid NOT NULL REFERENCES ticket_orders,
    validated_by_id uuid REFERENCES users,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id uuid NOT NULL,
    actor_id uuid NOT NULL REFERENCES users,
    action varchar(255) NOT NULL,
    target_type varchar(255) NOT NULL,
    target_id uuid NOT NULL,
    details varchar(500),
    created_at timestamp(6) NOT NULL,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------
-- Parte 2: columnas agregadas después de la primera versión del esquema
-- ---------------------------------------------------------------------
-- Cuenta de usuario: edición de email, contraseña y avatar; bloqueo.
ALTER TABLE users ADD COLUMN IF NOT EXISTS code_purpose varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pending_email varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_key varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked boolean DEFAULT false NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_reason varchar(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_at timestamp(6);
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at timestamp(6);

-- Imágenes de eventos y planos de recintos.
ALTER TABLE events ADD COLUMN IF NOT EXISTS image_key varchar(255);
ALTER TABLE in_person_events ADD COLUMN IF NOT EXISTS layout_image_key varchar(255);

-- Sectores: la tabla event_sectors se crea arriba si no existe.
ALTER TABLE ticket_orders ADD COLUMN IF NOT EXISTS sector_id uuid REFERENCES event_sectors;

-- Coordenadas: un esquema anterior las tenía como numeric(10,0) y redondeaba a grados
-- enteros. Repetirlo sobre columnas ya corregidas no cambia nada.
ALTER TABLE in_person_events ALTER COLUMN latitude TYPE numeric(10,7);
ALTER TABLE in_person_events ALTER COLUMN longitude TYPE numeric(10,7);

-- ---------------------------------------------------------------------
-- Parte 3: índices (reportes y estadísticas). Hibernate no los valida.
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_payments_status_date ON payments (payment_status, payment_date);
CREATE INDEX IF NOT EXISTS idx_tickets_event_status ON tickets (event_id, status);
CREATE INDEX IF NOT EXISTS idx_tickets_event_accessed ON tickets (event_id, accessed_at);
CREATE INDEX IF NOT EXISTS idx_ticket_orders_event ON ticket_orders (event_id);
