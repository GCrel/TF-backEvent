-- =====================================================================
-- data.sql - Datos iniciales para TF-backEvent
-- Todos los enums se guardan como STRING (@Enumerated(EnumType.STRING))
-- Herencia JOINED: se inserta primero en "events" y luego en la tabla hija
-- Contraseña de todos los usuarios: "password" (hash BCrypt)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) USERS (tabla padre: sin dependencias)
-- ---------------------------------------------------------------------
INSERT INTO users (id, name, email, password, system_role, date_birth, is_enabled, verification_code, verification_code_expires_at, verification_code_failed_attempts)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Ana García',  'ana.garcia@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'ADMIN', DATE '1990-05-12', TRUE, NULL, NULL, 0),
    ('22222222-2222-2222-2222-222222222222', 'Bruno López', 'bruno.lopez@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'USER', DATE '1995-09-23', TRUE, NULL, NULL, 0),
    ('33333333-3333-3333-3333-333333333333', 'Carla Méndez','carla.mendez@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'USER', DATE '1998-01-30', TRUE, NULL, NULL, 0),
    ('44444444-4444-4444-4444-444444444444', 'Diego Torres','diego.torres@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'USER', DATE '2000-11-05', FALSE,
     'ABC123', TIMESTAMP '2030-01-01 12:00:00', 0);

-- ---------------------------------------------------------------------
-- 2) EVENTS (tabla padre de la herencia JOINED; depende de nada)
-- ---------------------------------------------------------------------
INSERT INTO events (id, title, description, date_time, status, base_price)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'TechConf 2026', 'Conferencia anual de tecnología con charlas sobre IA, cloud y desarrollo web.',
     TIMESTAMP '2026-10-15 09:00:00', 'PUBLISHED', 150.00),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'Meetup Java & Coffee', 'Encuentro mensual de la comunidad Java de Buenos Aires.',
     TIMESTAMP '2026-09-20 18:30:00', 'PUBLISHED', 25.50),
    ('bbbbbbbb-0000-0000-0000-000000000001', 'Webinar: Microservicios con Spring Boot', 'Webinar en vivo sobre arquitectura de microservicios con Spring Boot y Docker.',
     TIMESTAMP '2026-09-01 19:00:00', 'PUBLISHED', 40.00),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'Workshop Online: Docker para Devs', 'Taller práctico virtual sobre contenedores y orquestación.',
     TIMESTAMP '2026-11-10 17:00:00', 'DRAFT', 60.00);

-- ---------------------------------------------------------------------
-- 3a) IN_PERSON_EVENTS (hija de events)
-- ---------------------------------------------------------------------
INSERT INTO in_person_events (id, address, latitude, longitude, capacity)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'Av. Corrientes 1234, Buenos Aires', -34.603722, -58.381592, 500),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'Calle Falsa 123, La Plata',         -34.921455, -57.954536, 80);

-- ---------------------------------------------------------------------
-- 3b) VIRTUAL_EVENTS (hija de events)
-- ---------------------------------------------------------------------
INSERT INTO virtual_events (id, access_link, platform, access_instructions)
VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001', 'https://meet.example.com/webinar-spring', 'Google Meet',
     'El enlace se habilita 15 minutos antes del inicio. Ingresá con tu correo registrado.'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'https://zoom.example.com/workshop-docker', 'Zoom',
     'Se enviará el código de sala por email un día antes del taller.');

-- ---------------------------------------------------------------------
-- 4a) TICKET_ORDERS (depende de users y events; una orden por compra)
-- ---------------------------------------------------------------------
INSERT INTO ticket_orders (id, buyer_id, event_id, quantity, unit_price, total_amount, created_at,
                           delivery_status, delivery_attempts, next_delivery_attempt_at, last_delivery_error, version)
VALUES
    ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000001',
     1, 150.00, 150.00, TIMESTAMP '2026-07-01 10:15:00', 'SENT', 1, NULL, NULL, 0),
    ('99999999-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000002',
     1, 25.50, 25.50, TIMESTAMP '2026-07-03 16:40:00', 'SENT', 1, NULL, NULL, 0),
    ('99999999-0000-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333', 'bbbbbbbb-0000-0000-0000-000000000001',
     1, 40.00, 40.00, TIMESTAMP '2026-07-10 21:05:00', 'NOT_REQUIRED', 0, NULL, NULL, 0),
    ('99999999-0000-0000-0000-000000000004', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001',
     1, 150.00, 150.00, TIMESTAMP '2026-06-25 08:00:00', 'NOT_REQUIRED', 0, NULL, NULL, 0);

-- ---------------------------------------------------------------------
-- 4b) TICKETS (depende de ticket_orders, users y events)
-- ---------------------------------------------------------------------
INSERT INTO tickets (id, bought_at, status, order_id, event_id, attendee_id)
VALUES
    ('cccccccc-0000-0000-0000-000000000001', TIMESTAMP '2026-07-01 10:15:00', 'ACTIVE',    '99999999-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222'),
    ('cccccccc-0000-0000-0000-000000000002', TIMESTAMP '2026-07-03 16:40:00', 'USED',      '99999999-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333'),
    ('cccccccc-0000-0000-0000-000000000003', TIMESTAMP '2026-07-10 21:05:00', 'ACTIVE',    '99999999-0000-0000-0000-000000000003', 'bbbbbbbb-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333'),
    ('cccccccc-0000-0000-0000-000000000004', TIMESTAMP '2026-06-25 08:00:00', 'CANCELLED', '99999999-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333');

-- ---------------------------------------------------------------------
-- 5) PAYMENTS (OneToOne con ticket_orders)
-- ---------------------------------------------------------------------
INSERT INTO payments (id, transaction_id, total_amount, payment_date, payment_status,
                      mp_status, status_detail, next_reconcile_at, version, order_id)
VALUES
    ('dddddddd-0000-0000-0000-000000000001', 'TXN-2026-000001', 150.00, TIMESTAMP '2026-07-01 10:16:30', 'APPROVED',
     'approved', 'accredited', TIMESTAMP '2026-07-01 10:16:30', 0, '99999999-0000-0000-0000-000000000001'),
    ('dddddddd-0000-0000-0000-000000000002', 'TXN-2026-000002',  25.50, TIMESTAMP '2026-07-03 16:42:10', 'APPROVED',
     'approved', 'accredited', TIMESTAMP '2026-07-03 16:42:10', 0, '99999999-0000-0000-0000-000000000002'),
    ('dddddddd-0000-0000-0000-000000000003', 'TXN-2026-000003',  40.00, TIMESTAMP '2026-07-10 21:06:00', 'PENDING',
     'in_process', NULL, TIMESTAMP '2026-07-10 21:06:00', 0, '99999999-0000-0000-0000-000000000003'),
    ('dddddddd-0000-0000-0000-000000000004', 'TXN-2026-000004', 150.00, TIMESTAMP '2026-06-25 08:01:45', 'REJECTED',
     'rejected', 'cc_rejected_other_reason', TIMESTAMP '2026-06-25 08:01:45', 0, '99999999-0000-0000-0000-000000000004');

-- ---------------------------------------------------------------------
-- 6) REFRESH_TOKEN (depende de users)
-- ---------------------------------------------------------------------
INSERT INTO refresh_token (id, token, expires_at, user_id)
VALUES
    ('eeeeeeee-0000-0000-0000-000000000001', 'refresh-token-admin-ana',      TIMESTAMP '2026-12-31 23:59:59', '11111111-1111-1111-1111-111111111111'),
    ('eeeeeeee-0000-0000-0000-000000000002', 'refresh-token-bruno-vigente',  TIMESTAMP '2026-12-31 23:59:59', '22222222-2222-2222-2222-222222222222'),
    ('eeeeeeee-0000-0000-0000-000000000003', 'refresh-token-carla-vencido', TIMESTAMP '2026-08-01 00:00:00',  '33333333-3333-3333-3333-333333333333');

-- ---------------------------------------------------------------------
-- 7) EVENT_PARTICIPATIONS (PK compuesta user_id + event_id + event_role;
--    depende de users y events)
-- ---------------------------------------------------------------------
INSERT INTO event_participations (user_id, event_id, event_role)
VALUES
    -- Organizadores
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000001', 'ORGANIZER'),
    ('22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000002', 'ORGANIZER'),
    ('22222222-2222-2222-2222-222222222222', 'bbbbbbbb-0000-0000-0000-000000000001', 'ORGANIZER'),
    -- Asistentes
    ('22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000001', 'ATTENDEE'),
    ('33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000002', 'ATTENDEE'),
    -- Controladores de acceso
    ('33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001', 'CONTROLLER');

-- ---------------------------------------------------------------------
-- 8) ORGANIZER_MP_ACCOUNTS (depende de users)
-- ---------------------------------------------------------------------
INSERT INTO organizer_mp_accounts (id, user_id, mp_access_token, mp_user_id, mp_email,
                                  linked_at, mp_refresh_token, mp_token_expires_at)
VALUES
    ('ffffffff-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
     'TEST-ACCESS-TOKEN-ANA-1234567890', '123456789', 'ana.garcia@example.com',
     TIMESTAMP '2026-09-01 10:00:00',
     'TEST-REFRESH-TOKEN-ANA-0987654321',
     TIMESTAMP '2027-09-01 10:00:00');

-- ---------------------------------------------------------------------
-- 9) MP_OAUTH_STATES (depende de users)
-- ---------------------------------------------------------------------
INSERT INTO mp_oauth_states (state, user_id, expires_at)
VALUES
    ('test-state-abc123', '22222222-2222-2222-2222-222222222222', TIMESTAMP '2026-12-31 23:59:59');
