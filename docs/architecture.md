# Arquitectura — TF-backEvent

Documento de referencia sobre la arquitectura interna del backend, el modelo de
dominio y los flujos de negocio principales. Para el detalle de cada endpoint ver
[`api.md`](api.md); para el porqué de las decisiones, [`decisions.md`](decisions.md).

## Índice

- [Visión general](#visión-general)
- [Organización por módulos](#organización-por-módulos)
- [Capas](#capas)
- [Modelo de dominio](#modelo-de-dominio)
- [Seguridad](#seguridad)
- [Manejo de errores](#manejo-de-errores)
- [Tareas programadas](#tareas-programadas)
- [Flujos principales](#flujos-principales)

## Visión general

TF-backEvent es una API REST **stateless** construida con Spring Boot. La
aplicación se organiza por **módulos de negocio** (package-by-feature) en lugar de
por capas técnicas globales. Cada módulo agrupa sus controladores, servicios,
repositorios, DTOs y excepciones.

```text
Cliente (Angular) ──HTTPS/JSON──▶ API REST (Spring Boot)
                                      │
      ┌────────────┬──────────────────┼──────────────────┬────────────────┐
      ▼            ▼                  ▼                  ▼                ▼
 PostgreSQL   MercadoPago       SMTP (email)      Scheduler        JWT / Security
 persistencia  pagos + OAuth    notificaciones    reintentos       filtro por request
```

Componentes externos:

- **PostgreSQL 15** — almacenamiento principal.
- **MercadoPago** — procesamiento de pagos, OAuth del organizador y webhooks.
- **SMTP** — envío de códigos de verificación y entradas.

## Organización por módulos

```text
org.gcrel.tfbackevent
├── auth/       Registro, login, verificación de email, tokens JWT y envío de correos.
├── event/      Ciclo de vida de eventos y ventana de acceso.
├── payment/    Órdenes de compra, integración MercadoPago, webhooks, QR y entrega.
├── access/     Validación de QR presencial, acceso virtual y controladores.
├── model/      Entidades JPA y enums compartidos del dominio.
└── common/     Seguridad, CORS, manejo global de excepciones y configuración transversal.
```

Cada módulo de negocio contiene los paquetes que necesita de esta lista (no todos
tienen todos; por ejemplo, `access` reutiliza repositorios de `event` y `payment`):

```text
<module>/
├── controller/   Endpoints HTTP (@RestController)
├── service/      Lógica de negocio y transacciones
├── repository/   Acceso a datos (Spring Data JPA)
├── dto/          request/ y response/ (records)
├── config/       Propiedades específicas del módulo (@ConfigurationProperties)
└── exception/    Excepciones de negocio del módulo
```

## Capas

| Capa | Responsabilidad |
|------|-----------------|
| **Controller** | Recibe la petición HTTP, valida el DTO (`@Validated`) y delega en el servicio. No contiene lógica de negocio. |
| **Service** | Orquesta reglas de negocio, transacciones (`@Transactional` / `TransactionTemplate`) y coordinación entre módulos. |
| **Repository** | Interfaces Spring Data JPA. Incluye consultas derivadas y consultas de actualización atómicas para operaciones concurrentes. |
| **Model** | Entidades JPA del dominio y enums. |
| **DTO** | `records` de entrada/salida que desacoplan la API del modelo de persistencia. |

Configuración transversal en `common/config`:

- `SecurityConfig` — cadena de filtros, CORS (`cors.allowed-origins`), política
  stateless y reglas de autorización.
- `JwtAuthenticationFilter` — extrae y valida el JWT en cada request.
- `GlobalExceptionHandler` — traduce excepciones a `ErrorResponse` con el código HTTP adecuado.

## Modelo de dominio

### Entidades

| Entidad | Tabla | Rol |
|---------|-------|-----|
| `User` | `users` | Usuario del sistema. Guarda credenciales, rol de sistema y datos de verificación. |
| `Event` (abstracta) | `events` | Datos comunes de un evento (título, fecha, precio, estado). |
| `InPersonEvent` | `in_person_events` | Evento presencial: dirección, geolocalización y capacidad. |
| `VirtualEvent` | `virtual_events` | Evento virtual: enlace, plataforma e instrucciones de acceso. |
| `TicketOrder` | `ticket_orders` | Orden de compra: agrupa las entradas adquiridas juntas por un comprador (cantidad, precio unitario, total) y guarda el estado de entrega por email. |
| `Ticket` | `tickets` | Entrada individual. Pertenece a una orden; guarda estado, evento, titular y datos de acceso. Cada una tiene su propio QR. |
| `Payment` | `payments` | Pago de MercadoPago asociado 1:1 a una orden. Las órdenes de eventos gratuitos no tienen pago. |
| `EventParticipation` | `event_participations` | Relación usuario–evento con su rol (`EventRole`). Clave compuesta. |
| `RefreshToken` | `refresh_token` | Refresh token emitido a un usuario. |
| `OrganizerMpAccount` | `organizer_mp_accounts` | Cuenta de MercadoPago vinculada por OAuth y sus tokens. |
| `MpOAuthState` | `mp_oauth_states` | `state` de un solo uso para el flujo OAuth de MercadoPago. |

### Herencia de eventos

`Event` usa herencia JPA **`JOINED`**: la tabla `events` guarda los campos comunes
y las tablas hijas (`in_person_events`, `virtual_events`) los específicos. El tipo
se resuelve en runtime con `event.getType()` (`EventType.IN_PERSON` / `VIRTUAL`).

### Enums

| Enum | Valores |
|------|---------|
| `SystemRole` | `USER`, `ADMIN` |
| `EventRole` | `ORGANIZER`, `ATTENDEE`, `CONTROLLER` |
| `EventType` | `IN_PERSON`, `VIRTUAL` |
| `EventStatus` | `DRAFT`, `PUBLISHED`, `CANCELLED`, `FINISHED` |
| `TicketStatus` | `PENDING`, `ACTIVE`, `USED`, `CANCELLED` |
| `PaymentStatus` | `PENDING`, `APPROVED`, `REJECTED` |
| `DeliveryStatus` | `NOT_REQUIRED`, `PENDING`, `PROCESSING`, `SENT`, `FAILED` |

> `EventRole.ATTENDEE` existe en el enum pero hoy no se asigna al comprar: la
> relación comprador–evento se obtiene de `ticket_orders` / `tickets`.

### Diagrama de relaciones

```text
users 1───N refresh_token
users 1───N event_participations N───1 events
users 1───0..1 organizer_mp_accounts
users 1───0..1 mp_oauth_states        (se reemplaza en cada /mp/connect)
users 1───N ticket_orders (buyer)
users 1───N tickets (attendee)
users 1───N tickets (validated_by, controlador que validó el QR)

events 1───N ticket_orders
events 1───N tickets
ticket_orders 1───N tickets           (N = quantity)
ticket_orders 1───0..1 payments       (sin pago si el evento es gratuito)
events ──JOINED──▶ in_person_events
events ──JOINED──▶ virtual_events
```

`event_participations` tiene clave primaria compuesta (`user_id`, `event_id`,
`event_role`), lo que permite que un mismo usuario tenga más de un rol en un mismo
evento (por ejemplo, organizador y controlador).

### Estados y transiciones

```text
EventStatus:
  DRAFT ──publish──▶ PUBLISHED ──(tarea programada, cierre de ventana)──▶ FINISHED
    │                  │   │
    │                  │   └──desvincular MercadoPago (evento pago y sin ventas)──▶ DRAFT
    │                  └──cancel (sin entradas vendidas)──▶ CANCELLED
    └──cancel──▶ CANCELLED

TicketStatus:
  Evento pago:
    PENDING ──pago aprobado──▶ ACTIVE ──validate-qr (presencial)──▶ USED
       │
       ├──pago rechazado / cancelado──▶ CANCELLED
       └──orden huérfana sin pago en MercadoPago──▶ CANCELLED
  Evento gratuito:
    (se emite directamente) ACTIVE ──validate-qr (presencial)──▶ USED

  Las entradas virtuales no pasan a USED: el acceso se registra en accessedAt.

DeliveryStatus (por orden):
  NOT_REQUIRED ──pago aprobado / orden gratuita──▶ PENDING ──▶ PROCESSING ──▶ SENT
                                                     ▲             │
                                                     │             └──fallo──▶ FAILED
                                                     └──reintento con backoff──┘
  FAILED sin próxima fecha = intentos agotados (solo se reintenta si el comprador
  pide el reenvío).
```

## Seguridad

- Sesión **stateless**: no se crean sesiones HTTP; cada request se autentica con el
  JWT.
- `JwtAuthenticationFilter` valida el access token y carga el `User` desde la base
  de datos, registrando la autoridad `ROLE_<SystemRole>`.
- Roles de evento (`ORGANIZER`, `CONTROLLER`, ...) **no** se resuelven en el filtro:
  se validan en cada servicio de negocio consultando `event_participations`
  (ver `EventAuthorizationService`).
- CORS se configura en `SecurityConfig` a partir de `cors.allowed-origins`
  (orígenes exactos con esquema, separados por coma) y aplica a `/api/**`.

### Reglas de autorización (`SecurityConfig`)

| Ruta | Acceso |
|------|--------|
| `/swagger-ui/**`, `/v3/api-docs/**` | Público (deshabilitado en `prod`) |
| `/api/v1/auth/{login,register,verify,refresh,resend-verification}` | Público |
| `/api/v1/webhooks/**` | Público (validado por firma HMAC) |
| `/api/v1/mp/callback` | Público (lo invoca MercadoPago al redirigir al navegador) |
| `GET /api/v1/events`, `GET /api/v1/events/{id}` | Público |
| `GET /api/v1/events/mine` | Autenticado |
| `/api/v1/admin/**` | Rol `ADMIN` (reservado; aún no hay endpoints) |
| Cualquier otra ruta | Autenticado |

### Tokens

| Token | Formato | Duración por defecto | Uso |
|-------|---------|----------------------|-----|
| Access token | JWT firmado con HMAC-SHA (HS256, HS384 o HS512 según el largo de la clave: 32, 48 o 64+ bytes) | 30 min | `Authorization: Bearer <token>` |
| Refresh token | UUID persistido en `refresh_token` | 7 días | Renovar el access token |

## Manejo de errores

`GlobalExceptionHandler` centraliza la traducción de excepciones a una respuesta
uniforme `ErrorResponse`:

```json
{
  "error": "El evento no está disponible para la compra de entradas.",
  "status": 409,
  "timestamp": "2026-10-03T18:30:00"
}
```

Las excepciones de negocio heredan de `AppException` y transportan su código HTTP
(`BadRequestException`, `NotFoundException`, `ConflictException`, `ForbiddenException`,
`UnauthorizedException`, `PaymentRequiredException`, `PreconditionRequiredException`,
`BadGatewayException`, etc.). En errores de validación (`400`) se agrega el mapa
`fields` con el detalle por campo; en el resto de los errores `fields` se omite.

El esquema completo de códigos y ejemplos está en [`api.md`](api.md#formato-de-error).

## Tareas programadas

La aplicación tiene `@EnableScheduling` y `@EnableAsync`. Tres componentes ejecutan
tareas periódicas (configurables por propiedades):

| Componente | Frecuencia | Función |
|------------|------------|---------|
| `EventService.finishPastEvents` | `event.finish.retry-delay-ms` (60 s) | Pasa a `FINISHED` los eventos `PUBLISHED` cuya ventana posterior ya cerró. |
| `PaymentRecoveryService.recoverPayments` | `payment.recovery.retry-delay-ms` (60 s) | Reconcilia pagos `PENDING` contra MercadoPago y resuelve órdenes huérfanas. |
| `DeliveryService.processDueDeliveries` | `ticket.delivery.retry-delay-ms` (5 s) | Envía por email las entradas de las órdenes pendientes de entrega y reintenta las fallidas. |

### Ventana de acceso

No se modela una fecha de fin del evento. La ventana se calcula **relativa a
`dateTime`** con `EventWindowProperties`:

- Ingreso habilitado desde `before-hours` (3 h) antes hasta `after-hours` (12 h) después.
- Compra habilitada mientras `PUBLISHED` y antes de `after-hours` después de `dateTime`.
- Un evento pasa a `FINISHED` al cerrar la ventana posterior.

## Flujos principales

### 1. Registro y verificación de email

```text
POST /auth/register
  └─ normaliza email, verifica que no exista, genera código de 6 dígitos
     (15 min, máx. 5 intentos) y envía el correo. La cuenta queda deshabilitada.

POST /auth/verify { email, code }
  ├─ código vencido o invalidado por 5 intentos fallidos → 400 (pedir uno nuevo).
  └─ código correcto → habilita la cuenta → devuelve par de tokens.

POST /auth/resend-verification { email }
  └─ genera un código nuevo y reinicia el contador de intentos.
```

### 2. Login, refresh y logout

```text
POST /auth/login      → valida la contraseña y luego que la cuenta esté verificada
                        (una contraseña incorrecta siempre responde 401, sin revelar
                        el estado de la cuenta). Elimina los refresh tokens previos
                        y emite un par nuevo.
POST /auth/refresh    → valida el refresh token (existe, vigente, no revocado) y
                        emite un nuevo access token. El refresh token NO rota.
POST /auth/logout     → elimina el refresh token indicado.
```

### 3. Creación y publicación de evento

```text
POST /events
  └─ crea el evento en DRAFT (InPersonEvent o VirtualEvent) y registra al usuario
     como ORGANIZER en event_participations. El precio puede ser 0 (gratuito).

PUT /events/{id}/publish
  └─ valida: es organizador, el evento es modificable y la fecha es futura.
     Si el evento tiene precio, exige cuenta de MercadoPago vinculada (si no, 428).
     → PUBLISHED

PUT /events/{id}
  └─ si el evento está PUBLISHED y el nuevo precio es > 0, también exige cuenta
     de MercadoPago vinculada. Con entradas vendidas no se puede cambiar precio
     ni fecha.
```

### 4. Compra de entradas (presencial y virtual)

```text
POST /tickets/purchase { eventId, quantity, [datos de pago] }
  1. Valida quantity ≤ ticket.purchase.max-quantity (10) y que el evento esté
     PUBLISHED y dentro de la ventana de compra.
  2. Reserva la orden en una transacción con bloqueo pesimista sobre el evento
     (findByIdForUpdate): verifica cupo (vendidas + quantity ≤ capacidad) y crea
     la TicketOrder con N tickets. Si el precio cambió entre la lectura y el
     bloqueo (gratuito ↔ pago), responde 409.

  Evento gratuito (precio 0):
  3. Los tickets se crean ACTIVE y se programa la entrega por email.
     No se llama a MercadoPago ni se crea Payment.
  4. Devuelve PurchaseResponse (status "approved", statusDetail "free") con un QR
     por entrada si el evento es presencial.

  Evento pago:
  3. Exige los datos de pago y obtiene un access token válido de la cuenta MP
     del organizador.
  4. Cobra precio × quantity en MercadoPago en un único pago, con clave de
     idempotencia y external_reference = id de la orden.
  5. handleNewPayment: actualiza todos los tickets de la orden y crea el Payment.
       - approved → tickets ACTIVE + entrega programada + QR por entrada (presencial)
       - rejected → tickets CANCELLED
       - pending  → tickets PENDING
  6. Devuelve PurchaseResponse con la lista de tickets.
```

Si MercadoPago rechaza la operación (400/422) los tickets se cancelan y se responde
`402`. Si el resultado es incierto (error 5xx o de red), la orden queda `PENDING`
sin pago para que la reconciliación la resuelva.

### 5. Confirmación por webhook y reconciliación

```text
POST /webhooks/payments
  ├─ valida firma HMAC-SHA256 (x-signature) y antigüedad del timestamp (< 5 min).
  ├─ ignora notificaciones que no sean de tipo payment.
  ├─ busca el Payment local; si no existe → 409 (PaymentNotificationPendingException).
  └─ consulta el pago real a MercadoPago y actualiza la orden y sus tickets.
```

Como respaldo (webhook perdido o pago sin confirmar), `PaymentRecoveryService`:

- Reconsulta pagos `PENDING` contra MercadoPago, con espera creciente entre
  consultas.
- Resuelve órdenes "huérfanas" (tickets `PENDING` sin `Payment` después de
  `payment.recovery.orphan-after-minutes`): busca el pago en MercadoPago por
  `external_reference`; si existe lo registra, si no cancela los tickets.
- Usa **bloqueo optimista** (`@Version`) para que la reconciliación y el webhook no
  apliquen dos veces el mismo cambio.

### 6. Entrega de entradas

```text
DeliveryService (cada 5 s)
  ├─ procesa órdenes con deliveryStatus PENDING o FAILED cuya fecha de intento venció.
  ├─ envía un único email por orden, solo con los tickets ACTIVE:
  │    presencial → un QR (ZXing) por entrada.
  │    virtual    → plataforma, enlace e instrucciones, indicando la cantidad.
  ├─ ante un fallo reintenta con backoff exponencial (1, 2, 4… min, tope 60 min)
  │  hasta ticket.delivery.max-attempts (8); luego queda FAILED sin reintentos.
  └─ recupera entregas "colgadas" (PROCESSING > 10 min) como un intento fallido.
```

`POST /tickets/{id}/resend-email` vuelve a encolar la entrega de la orden completa
del ticket (que debe estar `ACTIVE`) e inicia un nuevo ciclo de intentos. Respeta
un cooldown de 5 minutos después de un envío exitoso.

### 7. Vinculación de MercadoPago (OAuth)

```text
GET  /mp/connect      → genera un state de un solo uso (10 min) y devuelve la URL
                        de autorización. El frontend navega a esa URL.
GET  /mp/callback     → MercadoPago redirige el navegador con `code`; se intercambia
                        por tokens, se guarda la cuenta (OrganizerMpAccount) y se
                        redirige al frontend (mercadopago.frontend-return-url) con
                        ?status=linked o ?status=error&reason=...
GET  /mp/status       → indica si la cuenta está vinculada.
DELETE /mp/disconnect → desvincula. Bloquea con 409 si hay tickets PENDING/ACTIVE
                        en eventos PUBLISHED pagos. Los eventos PUBLISHED pagos sin
                        ventas vuelven a DRAFT; los gratuitos no se ven afectados.
```

Los tokens de MercadoPago se renuevan automáticamente al usarse, cuando están a
menos de 24 h de expirar (`getValidAccessToken`).

### 8. Control de acceso presencial (QR)

```text
POST /access/validate-qr { eventId, qrCode }
  1. Verifica que el solicitante sea ORGANIZER o CONTROLLER del evento.
  2. Verifica que el evento sea IN_PERSON, esté PUBLISHED y dentro de la ventana.
  3. Verifica que el ticket pertenezca al evento.
  4. Marca el ticket como USED de forma ATÓMICA
     (markAsUsedIfActive: solo si está ACTIVE); registra quién validó y cuándo.
  5. Devuelve el ticket, el asistente, el evento y la hora de validación.
```

La actualización atómica garantiza que dos escaneos simultáneos no permitan el
doble ingreso (ver pruebas de concurrencia). Como cada entrada de una orden tiene
su propio QR, una orden de N entradas permite N ingresos.

### 9. Acceso virtual

```text
GET /access/virtual/{ticketId}
  └─ el titular autenticado obtiene plataforma, enlace e instrucciones.
     Requiere ticket ACTIVE y evento VIRTUAL. No está limitado por la ventana de
     ingreso y registra la primera consulta (accessedAt) de forma idempotente.
```

El enlace de acceso es el mismo para todos los compradores del evento (lo define
el organizador); no se generan credenciales individuales.

### 10. Gestión de controladores

```text
POST   /events/{eventId}/controllers { email } → asigna CONTROLLER a un usuario habilitado.
GET    /events/{eventId}/controllers           → lista controladores.
DELETE /events/{eventId}/controllers/{userId}  → quita un controlador.
GET    /access/my-events                       → eventos PUBLISHED donde el usuario es controlador.
```

Solo el organizador puede asignar, listar o quitar; se validan duplicados, evento
cancelado/finalizado y que el organizador no se asigne a sí mismo.
