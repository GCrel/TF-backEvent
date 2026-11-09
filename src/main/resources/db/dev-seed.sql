-- =====================================================================
-- db/dev-seed.sql - Datos de prueba para el perfil dev (solo se referencia desde application-dev)
--
-- * Contraseña de todos los usuarios: "password" (hash BCrypt).
-- * Enums como STRING (@Enumerated(EnumType.STRING)).
-- * Herencia JOINED: se inserta primero en "events" y luego en la tabla hija.
-- * Las fechas son RELATIVAS al arranque (LOCALTIMESTAMP): como dev recrea el
--   esquema en cada inicio, los escenarios (evento próximo, en curso y
--   finalizado) se mantienen vigentes.
-- * No se siembran entregas PENDING ni pagos PENDING, para que las tareas
--   programadas no intenten usar el SMTP ni consultar MercadoPago al arrancar.
--
-- Escenarios:
--   Ana (ADMIN)    organiza TechConf (pago, presencial), el Webinar (pago,
--                  virtual) y el Hackathon (finalizado). Tiene MercadoPago
--                  vinculado (tokens ficticios: los cobros reales fallan).
--   Bruno          organiza el Meetup (gratuito, en curso: ventana de ingreso
--                  abierta) y el Workshop (borrador pago: al publicarlo responde
--                  428 porque no tiene MercadoPago). Compró 2 entradas de
--                  TechConf y usó su entrada del Hackathon.
--   Carla          controladora de TechConf y del Meetup. Tiene una entrada del
--                  Webinar, una entrada usada del Meetup y una compra rechazada
--                  de TechConf.
--   Diego          cuenta sin verificar; código de verificación 482913
--                  (vigente durante 1 día desde el arranque).
--   Elena          cuenta bloqueada por un administrador (login responde 403).
--   Bitácora       dos acciones de Ana sobre Elena (bloqueo y cierre de sesiones).
--
-- Nota: LOCALTIMESTAMP usa la zona horaria de PostgreSQL (UTC en el
-- contenedor). Si difiere de la del backend, los horarios se corren unas
-- horas, pero los escenarios tienen margen para seguir siendo válidos.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) USERS
-- ---------------------------------------------------------------------
INSERT INTO users (id, name, email, password, system_role, date_birth, is_enabled,
                   verification_code, verification_code_expires_at, verification_code_failed_attempts)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Ana García', 'ana.garcia@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'ADMIN', DATE '1990-05-12', TRUE,
     NULL, NULL, 0),
    ('22222222-2222-2222-2222-222222222222', 'Bruno López', 'bruno.lopez@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'USER', DATE '1995-09-23', TRUE,
     NULL, NULL, 0),
    ('33333333-3333-3333-3333-333333333333', 'Carla Méndez', 'carla.mendez@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'USER', DATE '1998-01-30', TRUE,
     NULL, NULL, 0),
    ('44444444-4444-4444-4444-444444444444', 'Diego Torres', 'diego.torres@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'USER', DATE '2000-11-05', FALSE,
     '482913', LOCALTIMESTAMP + INTERVAL '1 day', 0);

-- Usuaria bloqueada por Ana (ver la bitácora al final).
INSERT INTO users (id, name, email, password, system_role, date_birth, is_enabled,
                   verification_code_failed_attempts, blocked, blocked_reason, blocked_at)
VALUES
    ('55555555-5555-5555-5555-555555555555', 'Elena Ruiz', 'elena.ruiz@example.com',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'USER', DATE '1993-03-17', TRUE,
     0, TRUE, 'Publicidad no deseada en la descripción de eventos', LOCALTIMESTAMP - INTERVAL '2 days');

-- ---------------------------------------------------------------------
-- 2) EVENTS (tabla padre de la herencia JOINED)
--    aaaa... = presenciales, bbbb... = virtuales
-- ---------------------------------------------------------------------
INSERT INTO events (id, title, description, date_time, status, base_price)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'TechConf 2026',
     'Conferencia anual de tecnología con charlas sobre IA, cloud y desarrollo web.',
     date_trunc('day', LOCALTIMESTAMP) + INTERVAL '30 days 9 hours', 'PUBLISHED', 150.00),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'Meetup Java & Coffee',
     'Encuentro de la comunidad Java. Entrada libre y gratuita.',
     date_trunc('hour', LOCALTIMESTAMP) - INTERVAL '1 hour', 'PUBLISHED', 0.00),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'Hackathon Universitario',
     'Maratón de programación de 12 horas por equipos.',
     date_trunc('day', LOCALTIMESTAMP) - INTERVAL '10 days' + INTERVAL '10 hours', 'FINISHED', 80.00),
    ('bbbbbbbb-0000-0000-0000-000000000001', 'Webinar: Microservicios con Spring Boot',
     'Webinar en vivo sobre arquitectura de microservicios con Spring Boot y Docker.',
     date_trunc('day', LOCALTIMESTAMP) + INTERVAL '7 days 19 hours', 'PUBLISHED', 40.00),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'Workshop Online: Docker para Devs',
     'Taller práctico virtual sobre contenedores y orquestación.',
     date_trunc('day', LOCALTIMESTAMP) + INTERVAL '40 days 17 hours', 'DRAFT', 60.00);

-- ---------------------------------------------------------------------
-- 3a) IN_PERSON_EVENTS
-- ---------------------------------------------------------------------
INSERT INTO in_person_events (id, address, latitude, longitude, capacity)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'Av. Corrientes 1234, Buenos Aires', -34.603722, -58.381592, 500),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'Calle Falsa 123, La Plata',         -34.921455, -57.954536, 80),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'Av. San Martín 300, Chilecito',     -29.163056, -67.498056, 200);

-- ---------------------------------------------------------------------
-- 3b) VIRTUAL_EVENTS
-- ---------------------------------------------------------------------
INSERT INTO virtual_events (id, access_link, platform, access_instructions)
VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001', 'https://meet.example.com/webinar-spring', 'Google Meet',
     'El enlace se habilita 15 minutos antes del inicio. Ingresá con tu correo registrado.'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'https://zoom.example.com/workshop-docker', 'Zoom',
     'Se enviará el código de sala por email un día antes del taller.');

-- ---------------------------------------------------------------------
-- 4) EVENT_PARTICIPATIONS (PK compuesta user_id + event_id + event_role)
--    Los compradores no tienen rol por evento: se relacionan por sus órdenes.
-- ---------------------------------------------------------------------
INSERT INTO event_participations (user_id, event_id, event_role)
VALUES
    -- Organizadores
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000001', 'ORGANIZER'),
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000003', 'ORGANIZER'),
    ('11111111-1111-1111-1111-111111111111', 'bbbbbbbb-0000-0000-0000-000000000001', 'ORGANIZER'),
    ('22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000002', 'ORGANIZER'),
    ('22222222-2222-2222-2222-222222222222', 'bbbbbbbb-0000-0000-0000-000000000002', 'ORGANIZER'),
    -- Controladores de acceso
    ('33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001', 'CONTROLLER'),
    ('33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000002', 'CONTROLLER');

-- ---------------------------------------------------------------------
-- 5) ORGANIZER_MP_ACCOUNTS (tokens ficticios; vencen en ~150 días)
-- ---------------------------------------------------------------------
INSERT INTO organizer_mp_accounts (id, user_id, mp_access_token, mp_user_id, mp_email,
                                   linked_at, mp_refresh_token, mp_token_expires_at)
VALUES
    ('ffffffff-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
     'TEST-ACCESS-TOKEN-ANA-1234567890', '123456789', 'ana.garcia@example.com',
     LOCALTIMESTAMP - INTERVAL '30 days',
     'TEST-REFRESH-TOKEN-ANA-0987654321',
     LOCALTIMESTAMP + INTERVAL '150 days');

-- ---------------------------------------------------------------------
-- 6) TICKET_ORDERS (una orden por compra; la entrega por email es por orden)
-- ---------------------------------------------------------------------
INSERT INTO ticket_orders (id, buyer_id, event_id, quantity, unit_price, total_amount, created_at,
                           delivery_status, delivery_attempts, next_delivery_attempt_at, last_delivery_error, version)
VALUES
    -- Bruno: 2 entradas de TechConf, pagadas y enviadas
    ('99999999-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000001',
     2, 150.00, 300.00, LOCALTIMESTAMP - INTERVAL '5 days',
     'SENT', 1, LOCALTIMESTAMP - INTERVAL '5 days' + INTERVAL '5 minutes', NULL, 0),
    -- Carla: compra de TechConf rechazada por MercadoPago
    ('99999999-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001',
     1, 150.00, 150.00, LOCALTIMESTAMP - INTERVAL '4 days',
     'NOT_REQUIRED', 0, NULL, NULL, 0),
    -- Carla: 1 entrada del Webinar, pagada y enviada
    ('99999999-0000-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333', 'bbbbbbbb-0000-0000-0000-000000000001',
     1, 40.00, 40.00, LOCALTIMESTAMP - INTERVAL '3 days',
     'SENT', 1, LOCALTIMESTAMP - INTERVAL '3 days' + INTERVAL '5 minutes', NULL, 0),
    -- Ana: 2 entradas gratuitas del Meetup (sin pago)
    ('99999999-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000002',
     2, 0.00, 0.00, LOCALTIMESTAMP - INTERVAL '2 days',
     'SENT', 1, LOCALTIMESTAMP - INTERVAL '2 days' + INTERVAL '5 minutes', NULL, 0),
    -- Carla: 1 entrada gratuita del Meetup, ya usada
    ('99999999-0000-0000-0000-000000000005', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000002',
     1, 0.00, 0.00, LOCALTIMESTAMP - INTERVAL '2 days',
     'SENT', 1, LOCALTIMESTAMP - INTERVAL '2 days' + INTERVAL '5 minutes', NULL, 0),
    -- Bruno: 1 entrada del Hackathon (finalizado), usada
    ('99999999-0000-0000-0000-000000000006', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000003',
     1, 80.00, 80.00, LOCALTIMESTAMP - INTERVAL '20 days',
     'SENT', 1, LOCALTIMESTAMP - INTERVAL '20 days' + INTERVAL '5 minutes', NULL, 0);

-- ---------------------------------------------------------------------
-- 7) TICKETS (cada entrada tiene su propio id = contenido del QR)
-- ---------------------------------------------------------------------
INSERT INTO tickets (id, bought_at, status, order_id, event_id, attendee_id, accessed_at, validated_by_id)
VALUES
    ('cccccccc-0000-0000-0000-000000000001', LOCALTIMESTAMP - INTERVAL '5 days', 'ACTIVE',
     '99999999-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', NULL, NULL),
    ('cccccccc-0000-0000-0000-000000000002', LOCALTIMESTAMP - INTERVAL '5 days', 'ACTIVE',
     '99999999-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', NULL, NULL),
    ('cccccccc-0000-0000-0000-000000000003', LOCALTIMESTAMP - INTERVAL '4 days', 'CANCELLED',
     '99999999-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', NULL, NULL),
    ('cccccccc-0000-0000-0000-000000000004', LOCALTIMESTAMP - INTERVAL '3 days', 'ACTIVE',
     '99999999-0000-0000-0000-000000000003', 'bbbbbbbb-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', NULL, NULL),
    -- Entradas activas del Meetup en curso: se pueden validar por QR ahora mismo
    ('cccccccc-0000-0000-0000-000000000005', LOCALTIMESTAMP - INTERVAL '2 days', 'ACTIVE',
     '99999999-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', NULL, NULL),
    ('cccccccc-0000-0000-0000-000000000006', LOCALTIMESTAMP - INTERVAL '2 days', 'ACTIVE',
     '99999999-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', NULL, NULL),
    ('cccccccc-0000-0000-0000-000000000007', LOCALTIMESTAMP - INTERVAL '2 days', 'USED',
     '99999999-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333',
     LOCALTIMESTAMP - INTERVAL '30 minutes', '22222222-2222-2222-2222-222222222222'),
    ('cccccccc-0000-0000-0000-000000000008', LOCALTIMESTAMP - INTERVAL '20 days', 'USED',
     '99999999-0000-0000-0000-000000000006', 'aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222',
     date_trunc('day', LOCALTIMESTAMP) - INTERVAL '10 days' + INTERVAL '9 hours', '11111111-1111-1111-1111-111111111111');

-- ---------------------------------------------------------------------
-- 8) PAYMENTS (1:1 con las órdenes pagas; las gratuitas no tienen pago)
-- ---------------------------------------------------------------------
INSERT INTO payments (id, transaction_id, total_amount, payment_date, payment_status,
                      mp_status, status_detail, next_reconcile_at, version, order_id)
VALUES
    ('dddddddd-0000-0000-0000-000000000001', '1300000001', 300.00, LOCALTIMESTAMP - INTERVAL '5 days', 'APPROVED',
     'approved', 'accredited', NULL, 0, '99999999-0000-0000-0000-000000000001'),
    ('dddddddd-0000-0000-0000-000000000002', '1300000002', 150.00, LOCALTIMESTAMP - INTERVAL '4 days', 'REJECTED',
     'rejected', 'cc_rejected_insufficient_amount', NULL, 0, '99999999-0000-0000-0000-000000000002'),
    ('dddddddd-0000-0000-0000-000000000003', '1300000003', 40.00, LOCALTIMESTAMP - INTERVAL '3 days', 'APPROVED',
     'approved', 'accredited', NULL, 0, '99999999-0000-0000-0000-000000000003'),
    ('dddddddd-0000-0000-0000-000000000004', '1300000004', 80.00, LOCALTIMESTAMP - INTERVAL '20 days', 'APPROVED',
     'approved', 'accredited', NULL, 0, '99999999-0000-0000-0000-000000000006');

-- ---------------------------------------------------------------------
-- 9) REFRESH_TOKEN (formato UUID, como los emite la API)
-- ---------------------------------------------------------------------
INSERT INTO refresh_token (id, token, expires_at, user_id)
VALUES
    ('eeeeeeee-0000-0000-0000-000000000001', '5f0c6a1e-2b7d-4c3a-9e8f-1a2b3c4d5e01',
     LOCALTIMESTAMP + INTERVAL '7 days', '11111111-1111-1111-1111-111111111111'),
    ('eeeeeeee-0000-0000-0000-000000000002', '5f0c6a1e-2b7d-4c3a-9e8f-1a2b3c4d5e02',
     LOCALTIMESTAMP + INTERVAL '7 days', '22222222-2222-2222-2222-222222222222'),
    -- Vencido: /auth/refresh responde 401
    ('eeeeeeee-0000-0000-0000-000000000003', '5f0c6a1e-2b7d-4c3a-9e8f-1a2b3c4d5e03',
     LOCALTIMESTAMP - INTERVAL '1 day', '33333333-3333-3333-3333-333333333333');

-- ---------------------------------------------------------------------
-- 10) AUDIT_LOGS (bitácora de acciones de administración)
-- ---------------------------------------------------------------------
INSERT INTO audit_logs (id, actor_id, action, target_type, target_id, details, created_at)
VALUES
    ('ffffffff-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'USER_BLOCKED', 'USER',
     '55555555-5555-5555-5555-555555555555', 'Publicidad no deseada en la descripción de eventos',
     LOCALTIMESTAMP - INTERVAL '2 days'),
    ('ffffffff-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'USER_SESSIONS_REVOKED', 'USER',
     '55555555-5555-5555-5555-555555555555', NULL, LOCALTIMESTAMP - INTERVAL '2 days' + INTERVAL '1 minute');
