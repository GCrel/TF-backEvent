# Decisiones de diseño — TF-backEvent

Registro de las decisiones técnicas relevantes, al estilo *Architecture Decision
Record* (ADR), y de las **limitaciones conocidas** del sistema.

Para el detalle funcional ver [`architecture.md`](architecture.md) y [`api.md`](api.md).

## Cómo mantener este documento

- Cada decisión tiene un **estado**: `Aceptada` o `Reemplazada por ADR-XXX`.
- Si una decisión cambia, **no se reescribe**: se marca como reemplazada y se
  agrega una nueva con el contexto del cambio. Así queda la historia del diseño.
- Solo se registran decisiones con alternativas reales y consecuencias que
  conviene recordar; los detalles de implementación van en `architecture.md`.

## Índice

| ADR | Decisión | Estado |
|-----|----------|--------|
| [001](#adr-001-api-rest-stateless-con-jwt) | API REST stateless con JWT | Aceptada |
| [002](#adr-002-el-refresh-token-no-rota) | El refresh token no rota | Aceptada |
| [003](#adr-003-verificación-de-email-con-código-de-6-dígitos) | Verificación de email con código de 6 dígitos | Aceptada |
| [004](#adr-004-herencia-jpa-joined-para-eventos) | Herencia JPA JOINED para eventos | Aceptada |
| [005](#adr-005-ventana-de-acceso-relativa-a-datetime) | Ventana de acceso relativa a `dateTime` | Aceptada |
| [006](#adr-006-roles-por-evento-vía-event_participations) | Roles por evento vía `event_participations` | Aceptada |
| [007](#adr-007-oauth-de-mercadopago-por-organizador) | OAuth de MercadoPago por organizador | Aceptada |
| [008](#adr-008-confirmación-de-pago-por-webhook--reconciliación) | Confirmación de pago por webhook + reconciliación | Aceptada |
| [009](#adr-009-estrategias-de-concurrencia) | Estrategias de concurrencia | Aceptada |
| [010](#adr-010-entrega-asíncrona-de-entradas-por-email) | Entrega asíncrona de entradas por email | Aceptada |
| [011](#adr-011-ocultar-la-existencia-de-recursos-no-públicos) | Ocultar la existencia de recursos no públicos | Aceptada |
| [012](#adr-012-las-credenciales-virtuales-no-viajan-en-eventresponse) | Las credenciales virtuales no viajan en `EventResponse` | Aceptada |
| [013](#adr-013-órdenes-de-compra-con-varias-entradas) | Órdenes de compra con varias entradas | Aceptada |
| [014](#adr-014-eventos-gratuitos-sin-mercadopago) | Eventos gratuitos sin MercadoPago | Aceptada |
| [015](#adr-015-oauth-de-mercadopago-adaptado-a-una-spa) | OAuth de MercadoPago adaptado a una SPA | Aceptada |
| [016](#adr-016-imágenes-en-volumen-local-servidas-por-nginx) | Imágenes en volumen local servidas por Nginx | Aceptada |
| [017](#adr-017-sectores-con-precio-y-cupo-propios-sobre-un-plano) | Sectores con precio y cupo propios sobre un plano | Aceptada |
| [018](#adr-018-edición-de-datos-de-la-cuenta-con-código-de-verificación) | Edición de datos de la cuenta con código de verificación | Aceptada |
| [019](#adr-019-tokens-de-sesión-en-cookies-httponly) | Tokens de sesión en cookies `HttpOnly` | Aceptada |

## ADR-001: API REST stateless con JWT

**Estado:** Aceptada.

**Contexto.** El frontend (Angular) consume la API por HTTPS y no debe depender de
sesiones de servidor.

**Decisión.** Autenticación sin estado mediante dos tokens:

- **Access token** (JWT firmado con HMAC, 30 min) — viaja en una cookie `HttpOnly` (ADR-019).
- **Refresh token** (UUID persistido, 7 días) — permite renovar sin volver a iniciar sesión.

**Consecuencias.**

- Escalabilidad horizontal sencilla (no hay estado de sesión en el servidor).
- Los access tokens **no se pueden revocar** individualmente; expiran por tiempo.
  La revocación efectiva se hace sobre los refresh tokens (un nuevo login elimina
  los anteriores).

## ADR-002: El refresh token no rota

**Estado:** Aceptada.

**Contexto.** En muchos esquemas el refresh token se rota en cada uso (*refresh
token rotation*) para detectar su robo.

**Decisión.** `POST /auth/refresh` **devuelve el mismo refresh token** y solo emite
un nuevo access token.

**Consecuencias.**

- Implementación más simple y menos escrituras en base de datos.
- Un refresh token filtrado sigue siendo válido hasta su expiración (7 días) o
  hasta un nuevo login. Simplificación aceptada para el alcance del proyecto.

## ADR-003: Verificación de email con código de 6 dígitos

**Estado:** Aceptada.

**Contexto.** Se necesita verificar que el correo de registro sea real.

**Decisión.** Al registrarse se genera un código numérico de 6 dígitos con
`SecureRandom`, válido **15 minutos** y con un máximo de **5 intentos** (luego se
invalida y hay que pedir otro). La cuenta permanece deshabilitada hasta verificar.
Al verificar se devuelve directamente el par de tokens.

**Consecuencias.**

- No hace falta iniciar sesión después de verificar.
- El registro depende de un servidor SMTP configurado.

## ADR-004: Herencia JPA JOINED para eventos

**Estado:** Aceptada.

**Contexto.** Los eventos presenciales y virtuales comparten atributos (título,
fecha, precio, estado) pero tienen campos propios.

**Decisión.** `Event` es una entidad abstracta con `@Inheritance(strategy = JOINED)`;
`InPersonEvent` y `VirtualEvent` son las subclases.

**Alternativas descartadas.** Tabla única con columnas anulables (*single table*) y
*table per class*.

**Consecuencias.**

- Cada tabla solo tiene las columnas que le corresponden y pueden ser `NOT NULL`
  (por ejemplo, `capacity` en eventos presenciales).
- Las consultas polimórficas requieren `JOIN` a las tablas hijas.

## ADR-005: Ventana de acceso relativa a dateTime

**Estado:** Aceptada.

**Contexto.** Un evento tiene un inicio, pero no necesariamente una duración. Hace
falta definir cuándo se puede comprar y cuándo se puede ingresar.

**Decisión.** **No se modela `endDateTime`.** La ventana se calcula con
`EventWindowProperties`, por defecto:

- Ingreso: desde 3 h antes hasta 12 h después de `dateTime`.
- Compra: mientras esté `PUBLISHED`, hasta 12 h después de `dateTime`.
- Cierre automático a `FINISHED` al terminar la ventana posterior.

**Consecuencias.**

- La compra puede hacerse con cualquier anticipación.
- Los límites son globales (`event.access-window.*`), no por evento.
- No contempla eventos de varios días.

## ADR-006: Roles por evento vía event_participations

**Estado:** Aceptada.

**Contexto.** El rol de una persona varía según el evento (organizador de uno,
controlador de otro). No debe ser un atributo fijo del usuario.

**Decisión.** El rol de sistema (`SystemRole`) es fijo del usuario; el rol en un
evento (`EventRole`) se guarda en `event_participations` con clave primaria
compuesta (`user_id`, `event_id`, `event_role`).

**Consecuencias.**

- Un usuario puede tener varios roles en el mismo evento.
- Las autorizaciones por evento se validan en los servicios
  (`EventAuthorizationService`), no en el filtro de seguridad.

## ADR-007: OAuth de MercadoPago por organizador

**Estado:** Aceptada. Complementada por [ADR-014](#adr-014-eventos-gratuitos-sin-mercadopago)
y [ADR-015](#adr-015-oauth-de-mercadopago-adaptado-a-una-spa).

**Contexto.** El dinero de las entradas debe acreditarse al organizador, no a la
plataforma.

**Decisión.** Cada organizador vincula su propia cuenta de MercadoPago mediante
OAuth. Los pagos se procesan con el access token del organizador y los fondos se
acreditan directamente en su cuenta. La plataforma no intermedia fondos.

**Consecuencias.**

- Para publicar un evento **con precio** el organizador debe tener la cuenta
  vinculada (`428 Precondition Required`).
- Los tokens de MercadoPago se renuevan automáticamente al usarse, cuando les
  quedan menos de 24 h.
- Se almacenan tokens de terceros en la base (`organizer_mp_accounts`), lo que
  exige proteger esa información.

## ADR-008: Confirmación de pago por webhook + reconciliación

**Estado:** Aceptada.

**Contexto.** El resultado de un pago puede llegar en la respuesta síncrona, por
webhook o quedar incierto por fallos de red.

**Decisión.** Doble mecanismo:

1. **Webhook** (`POST /webhooks/payments`) con validación de firma HMAC-SHA256 y
   control de antigüedad del timestamp (< 5 min).
2. **Tarea de reconciliación** (`PaymentRecoveryService`) que reconsulta pagos
   `PENDING` y resuelve órdenes sin pago registrado buscándolas en MercadoPago por
   `external_reference`.

**Consecuencias.**

- Aunque se pierda un webhook, el pago se reconcilia.
- El procesamiento debe ser **idempotente** (un pago no se aplica dos veces).
- Un webhook de un pago aún no registrado responde `409` para que MercadoPago
  reintente.

## ADR-009: Estrategias de concurrencia

**Estado:** Aceptada.

**Contexto.** Varias operaciones críticas pueden ejecutarse simultáneamente.

**Decisión por operación:**

| Operación | Mecanismo | Motivo |
|-----------|-----------|--------|
| Reserva de cupo al comprar | Bloqueo pesimista sobre el evento (`findByIdForUpdate`) | Evitar sobreventa con compras simultáneas. |
| Validación de QR | `UPDATE ... WHERE status = ACTIVE` atómico (`markAsUsedIfActive`) | Evitar doble ingreso con escaneos simultáneos. |
| Actualización de pagos y entregas | Bloqueo optimista `@Version` en `Payment` y `TicketOrder` | Evitar que webhook, reconciliación y entrega apliquen el mismo cambio dos veces. |

**Consecuencias.**

- Las carreras entre procesos concurrentes se resuelven sin doble efecto.
- El bloqueo pesimista serializa las compras de un mismo evento (impacto acotado).
- `@Version` puede lanzar `ObjectOptimisticLockingFailureException`, tratado como
  "ya procesado por otro proceso".

## ADR-010: Entrega asíncrona de entradas por email

**Estado:** Aceptada.

**Contexto.** El envío de correo es lento y falla de forma transitoria; no debe
bloquear la respuesta de compra ni perderse ante fallos.

**Decisión.** `DeliveryService` envía las entradas como tarea programada, un email
por orden. Ante fallos reintenta con **backoff exponencial** (1, 2, 4… minutos,
tope de 60) hasta un máximo de intentos configurable; agotados, la orden queda en
`FAILED` y solo se reintenta si el comprador pide el reenvío. Las entregas colgadas
en `PROCESSING` (> 10 min) cuentan como intento fallido.

**Consecuencias.**

- La compra responde sin esperar al correo.
- La entrega es *at-least-once*: ante un fallo ambiguo el correo podría repetirse
  (aceptable; el reenvío manual tiene un cooldown de 5 min).
- Un error permanente (por ejemplo, una dirección inválida) no genera reintentos
  infinitos.
- El estado de entrega queda registrado en `TicketOrder` (`deliveryStatus`,
  `deliveryAttempts`, `lastDeliveryError`).

## ADR-011: Ocultar la existencia de recursos no públicos

**Estado:** Aceptada.

**Contexto.** Un evento en `DRAFT`/`CANCELLED`/`FINISHED` no debe ser visible para
terceros.

**Decisión.** Para eventos no publicados, `GET /events/{id}` responde `404 Not
Found` (en lugar de `403`) a quien no sea el organizador.

**Consecuencias.**

- No se revela la existencia del recurso (previene enumeración).
- Mensaje menos preciso para el cliente.

## ADR-012: Las credenciales virtuales no viajan en EventResponse

**Estado:** Aceptada.

**Contexto.** El enlace y las instrucciones de acceso de un evento virtual solo
deben verlos quienes tengan una entrada `ACTIVE`.

**Decisión.** `accessLink` y `accessInstructions` **se reciben** al crear/actualizar
el evento, pero **no se exponen** en `EventResponse`. Se entregan por correo tras la
compra o vía `GET /access/virtual/{ticketId}` al titular.

**Consecuencias.**

- No se filtran enlaces de acceso en los listados públicos de eventos.
- El enlace es el mismo para todos los compradores: quien lo reciba puede
  compartirlo (ver limitaciones).

## ADR-013: Órdenes de compra con varias entradas

**Estado:** Aceptada.

**Contexto.** Un comprador puede querer varias entradas para el mismo evento (por
ejemplo, para regalar). Inicialmente cada compra generaba un único ticket con su
propio pago, y se impedía tener más de una compra pendiente o activa por evento.

**Decisión.** Se introduce `TicketOrder`: una compra elige una cantidad
(`quantity`, máximo configurable `ticket.purchase.max-quantity`) y genera una orden
con N tickets, cada uno con su propio QR, todos asociados al mismo comprador. El
pago (si corresponde) y el estado de entrega por email pertenecen a la orden.

**Alternativas descartadas.** Hacer N compras individuales (N pagos, N comisiones y
N emails) o relacionar un pago con varios tickets sin una entidad intermedia (no
deja un lugar natural para la entrega de los eventos gratuitos, ver ADR-014).

**Consecuencias.**

- Un solo pago y un solo email por compra; idempotencia y `external_reference`
  en MercadoPago usan el id de la orden.
- Se elimina la restricción de una compra por persona y evento.
- Cada entrada se valida de forma independiente en la puerta.

## ADR-014: Eventos gratuitos sin MercadoPago

**Estado:** Aceptada.

**Contexto.** Los organizadores necesitan publicar eventos gratuitos, y no tiene
sentido exigir una cuenta de cobro ni datos de tarjeta para ellos.

**Decisión.** Si el precio del evento es 0, la compra emite las entradas
directamente en `ACTIVE`, sin llamar a MercadoPago ni crear `Payment`, y los datos
de pago son opcionales. Publicar un evento gratuito no exige cuenta vinculada;
pasarlo a pago estando publicado sí.

**Consecuencias.**

- Mismo flujo de compra y entrega para eventos gratuitos y pagos (la orden es el
  punto común).
- Si el precio cambia entre la lectura y el bloqueo del evento, la compra responde
  `409` para no emitir gratis una entrada que debía cobrarse.
- Desvincular MercadoPago no afecta a los eventos gratuitos.

## ADR-015: OAuth de MercadoPago adaptado a una SPA

**Estado:** Aceptada.

**Contexto.** El frontend es una SPA que llama a la API por XHR con un Bearer
token. Una respuesta `302` hacia otro dominio no puede seguirse desde XHR, y el
callback lo recibe el navegador del organizador, no la SPA.

**Decisión.** `GET /mp/connect` devuelve la URL de autorización en JSON y el
frontend navega a ella. `GET /mp/callback` termina el intercambio y **redirige al
frontend** (`mercadopago.frontend-return-url`) con `?status=linked` o
`?status=error&reason=...` en lugar de devolver JSON.

**Consecuencias.**

- El organizador vuelve a la aplicación con el resultado de la vinculación.
- El backend necesita conocer la URL de retorno del frontend (variable de entorno
  en producción).

## ADR-016: Imágenes en volumen local servidas por Nginx

**Estado:** Aceptada.

**Contexto.** Los eventos necesitan una imagen de portada. El despliegue es una sola
VM con Docker Compose, y el volumen de imágenes esperado es chico. Las alternativas
eran guardar los binarios en PostgreSQL, en un almacenamiento de objetos externo
(OCI Object Storage, Cloudflare R2, S3) o en un servicio de imágenes (Cloudinary).

**Decisión.**

- **Almacenamiento híbrido.** El archivo se guarda en un volumen nombrado de Docker
  (`uploads`, montado en `/app/uploads`) y la base solo guarda su clave
  (`events.image_key`, p. ej. `3f2a….webp`). `EventResponse.imageUrl` expone la
  ruta **relativa** `/api/v1/images/{key}`, sin acoplar los datos a un dominio.
- **Escritura en Spring, lectura en Nginx.** El backend solo recibe, valida y
  guarda (`PUT /events/{id}/image`) o borra (`DELETE /events/{id}/image`). Nginx
  atiende `GET /api/v1/images/{key}` leyendo el mismo volumen en **solo lectura**,
  así las descargas no ocupan hilos ni memoria de la JVM. Nginx también corre en
  desarrollo, para que ambos entornos tengan la misma arquitectura.
- **Nombres generados por el servidor.** Cada archivo se renombra a un UUID con la
  extensión del formato detectado. El nombre que envía el cliente nunca llega al
  disco: eso evita path traversal, la sobrescritura accidental o maliciosa de
  otros archivos y las extensiones falsas. Evitar colisiones es un beneficio
  secundario.
- **Validación por contenido.** Solo se aceptan JPEG, PNG y WebP, reconocidos por sus
  primeros bytes (no por la extensión ni el `Content-Type` del cliente). SVG queda
  excluido porque puede contener scripts. Nginx sirve con
  `X-Content-Type-Options: nosniff` y solo acepta rutas `<uuid>.(webp|jpg|png)`.
- **Límites.** 5 MB en Spring y `client_max_body_size 6m` en Nginx. Redimensionar
  y comprimir (preferentemente a WebP) es responsabilidad del frontend.
- **Consistencia.** El archivo nuevo se escribe antes de actualizar la base; la
  imagen sobrante (la anterior si la transacción se confirma, la nueva si se
  revierte) se borra al cerrar la transacción.

**Consecuencias.**

- No hay servicios, SDKs ni credenciales externas, y la base no crece con binarios.
- Las imágenes cambian de URL al reemplazarse, así que se cachean como inmutables.
- El volumen `uploads` debe respaldarse aparte de la base.
- Nginx y el backend deben compartir el volumen, es decir, correr en el mismo
  servidor. Escalar a varios servidores requeriría pasar a un almacenamiento de
  objetos (solo cambiaría `ImageStorageService` y la URL).
- Un fallo al borrar deja archivos huérfanos; no hay limpieza automática.
- Las imágenes de eventos en borrador son públicas para quien conozca la clave
  (aleatoria, no adivinable).

## ADR-017: Sectores con precio y cupo propios sobre un plano

**Estado:** Aceptada.

**Contexto.** Un evento presencial tenía un único precio y una única capacidad. Se
pidió dividirlo en sectores (VIP, General…) que el organizador dibuja sobre una foto
del recinto, y que el comprador elija el sector en el plano. No todos los
organizadores tienen un plano del lugar, así que el plano debe ser opcional. La
detección automática de sectores se descartó por requerir modelos de IA.

**Decisión.**

- **Plano opcional.** Los sectores (nombre, precio, cupo) no dependen del plano. Si
  hay plano, cada sector puede llevar su polígono; si no, se guarda sin geometría y
  el frontend los muestra como lista. Quitar el plano no borra los sectores.
- **El backend no interpreta la imagen.** El plano se guarda como la portada
  (ADR-016) y cada sector dibujado es un polígono de 3 a 20 vértices con coordenadas
  **normalizadas de 0 a 1** respecto del ancho y alto de la imagen, para que cualquier
  pantalla dibuje lo mismo. Los vértices se guardan como `jsonb`; el backend solo
  valida su rango. No se valida solapamiento.
- **Guardado en bloque.** `PUT /events/{id}/sectors` recibe la lista completa (con
  `id` actualiza, sin `id` crea, lo que falta borra) en una transacción, porque el
  editor del frontend guarda todo junto.
- **Valores derivados.** Con sectores, `capacity` del evento es la suma de sus
  capacidades y `basePrice` el mínimo de sus precios, para que tarjetas, filtros y
  disponibilidad sigan funcionando sin cambios.
- **"Pago" no depende de `basePrice`.** Un evento es pago si `basePrice > 0` **o**
  algún sector tiene precio. Así lo evalúan la publicación y la desvinculación de
  MercadoPago; con solo `basePrice`, un evento con un sector gratis y otro pago se
  trataría como gratuito.
- **Bloqueo por evento.** La compra y la edición de sectores bloquean la fila del
  evento (ADR-009), no la del sector. Serializa las compras de todos los sectores de
  un evento, algo imperceptible a esta escala, y evita que una edición compita con una
  compra.
- **Protección de ventas.** Un sector con entradas pendientes, activas o usadas no se
  borra, no cambia de precio y no baja su capacidad por debajo de lo ocupado. Un
  evento con entradas vendidas no puede pasar de "sin sectores" a "con sectores" ni al
  revés. La orden guarda el sector, y el precio de la orden es el del sector.

**Consecuencias.**

- Un evento sin sectores funciona exactamente como antes.
- El controlador ve el sector de cada entrada al validarla (`sectorName`).
- Al borrar un sector sin entradas vigentes, sus órdenes canceladas pierden la
  referencia al sector.
- El organizador puede dibujar sectores superpuestos; el backend no lo detecta.

## ADR-018: Edición de datos de la cuenta con código de verificación

**Estado:** Aceptada.

**Contexto.** El usuario debe poder cambiar su email, su contraseña, su nombre y su
avatar. El email es además la identidad dentro del JWT (`subject`), y la contraseña y
el email son la puerta de recuperación de la cuenta.

**Decisión.**

- **Contraseña actual + código.** Cambiar el email o la contraseña exige la contraseña
  actual y un código de 6 dígitos por correo. Con solo un access token robado no se
  puede tomar la cuenta. El código del cambio de email va al email **nuevo** (prueba
  que se tiene acceso a él); el de contraseña, al email actual.
- **Mismo código, con propósito.** Se reutilizan las columnas `verification_code*` del
  alta de cuenta, que ya traían vencimiento y máximo de 5 intentos, y se agrega
  `code_purpose`. El alta solo ocurre en cuentas sin habilitar y los cambios solo en
  habilitadas, así que no se pisan; el propósito evita cruzar el cambio de email con el
  de contraseña. Un pedido nuevo reemplaza al anterior.
- **Sesión nueva tras el cambio.** Como el JWT lleva el email, cambiarlo invalida los
  access tokens vigentes. Ambos cambios borran los refresh tokens (cierran las demás
  sesiones) y devuelven un par de tokens nuevo.
- **Nombre y avatar sin código.** No comprometen el acceso a la cuenta. El avatar usa
  el almacenamiento de imágenes de ADR-016 y se borra el archivo anterior al cerrar la
  transacción, igual que las portadas.

**Consecuencias.**

- Los access tokens emitidos en otros dispositivos siguen siendo válidos hasta
  expirar tras un cambio de contraseña (JWT sin estado). Tras un cambio de email se
  invalidan de inmediato porque el filtro busca al usuario por el email del token.
- No hay límite de frecuencia al pedir códigos, igual que en el reenvío de
  verificación: un usuario autenticado podría disparar muchos correos.
- Dos subidas simultáneas de avatar del mismo usuario pueden dejar un archivo
  huérfano (no se bloquea la fila del usuario como se hace con los eventos).

## ADR-019: Tokens de sesión en cookies `HttpOnly`

**Estado:** Aceptada. Reemplaza el header `Authorization` de ADR-001.

**Contexto.** El frontend guardaba los tokens en `localStorage`, donde cualquier script
inyectado (XSS) puede leerlos y llevárselos. Una cookie `HttpOnly` no es accesible desde
JavaScript.

**Decisión.**

- **Dos cookies `HttpOnly`.** `access_token` (ruta `/api`) y `refresh_token` (ruta
  `/api/v1/auth`, para que solo viaje a refresh y logout). Son `Secure` y `SameSite=Strict`
  por defecto; `COOKIE_SECURE` y `COOKIE_SAME_SITE` permiten ajustarlas (`false` en
  desarrollo sobre `http`; `None` solo si frontend y API están en sitios distintos).
- **Los tokens ya no van en el cuerpo ni se leen del header.** `login`, `verify` y los
  cambios de email/contraseña responden `204` y fijan las cookies; el front obtiene al
  usuario con `GET /auth/me`. `refresh` lee la cookie y renueva solo el access token.
  `logout` es público, borra el refresh token si existe y vacía las cookies.
- **CSRF sin token adicional.** Con cookies el navegador adjunta la sesión automáticamente,
  así que se agrega defensa en dos capas: `SameSite` evita que las cookies viajen en
  peticiones iniciadas desde otro sitio, y CORS (con credenciales y orígenes exactos)
  rechaza con `403` cualquier petición, preflight o no, cuyo `Origin` no esté en
  `CORS_ALLOWED_ORIGINS`. Se descartó el token CSRF de Spring Security: exigiría que el
  frontend lo reenvíe en cada mutación y excluir webhooks y callbacks, a cambio de poco
  frente a esas dos capas.

**Consecuencias.**

- El frontend debe enviar todas las peticiones con credenciales (`withCredentials`) y
  desde un origen listado en CORS. Un cliente que no sea un navegador (scripts, apps
  móviles) ya no obtiene tokens utilizables.
- Un XSS ya no puede robar los tokens, pero sigue pudiendo hacer peticiones como el
  usuario mientras la página esté abierta; la defensa contra XSS sigue siendo del frontend.
- Si frontend y API quedaran en dominios distintos habría que usar `SameSite=None`, que
  deja el control de CSRF solo en la validación de `Origin`.
- Los access tokens siguen sin poder revocarse (ADR-001); `logout` solo invalida el
  refresh token.

## Limitaciones conocidas

1. **Desvinculación de MercadoPago con ventana de carrera.** `DELETE /mp/disconnect`
   verifica que no haya tickets `PENDING`/`ACTIVE` en eventos pagos publicados y
   luego revierte esos eventos a `DRAFT`. Una compra que se reserve justo en ese
   intervalo podría quedar `PENDING` sin token del organizador.
2. **Refresh token sin rotación** (ver [ADR-002](#adr-002-el-refresh-token-no-rota)).
3. **Access tokens no revocables** de forma individual (stateless por diseño).
4. **Ventana de acceso global, no por evento** (ver [ADR-005](#adr-005-ventana-de-acceso-relativa-a-datetime)).
5. **Sin reembolsos ni contracargos.** Solo se procesan pagos `PENDING`; si un pago
   aprobado se reembolsa o tiene un contracargo en MercadoPago, las entradas siguen
   activas. Tampoco se puede cancelar un evento con entradas vendidas.
6. **Enlace virtual compartido.** Todos los compradores de un evento virtual
   reciben el mismo enlace; no hay credenciales individuales ni control de reventa.
7. **Tareas programadas sin coordinación entre instancias.** Con varias instancias
   del backend, cada una ejecutaría los `@Scheduled` (ver [`deployment.md`](deployment.md#operación-y-escalado)).
8. **Módulo de reportes y estadísticas no implementado** (figura en el alcance del
   sistema, pero aún no existe).
9. **No hay endpoints de administración.** `SecurityConfig` reserva
   `/api/v1/admin/**` para `ROLE_ADMIN`, pero todavía no existe ninguno.
10. **Sin streaming en vivo:** los eventos virtuales usan un enlace externo (Zoom,
    Meet, etc.).
11. **Imágenes huérfanas sin limpieza.** Si falla el borrado de un archivo de
    imagen, queda en el volumen (ver [ADR-016](#adr-016-imágenes-en-volumen-local-servidas-por-nginx)).
