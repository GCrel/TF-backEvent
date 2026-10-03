# Documentación de la API - TF-backEvent

## Información general

- **Base URL:** `http://localhost:8085`
- **Formato de datos:** JSON
- **Autenticación:** Bearer Token (JWT)
- **Documentación interactiva (perfil `dev`):** `http://localhost:8085/swagger-ui.html` (`/v3/api-docs` para el JSON OpenAPI)

> En el perfil `prod` Swagger está deshabilitado (`springdoc.swagger-ui.enabled=false`).

### Autenticación

La mayoría de los endpoints requieren un token de acceso en el header:

```
Authorization: Bearer <access-token>
```

El access token es un JWT de corta duración. Para renovar la sesión se debe enviar el `refreshToken` a `POST /api/v1/auth/refresh`.

Los endpoints protegidos devuelven `401 Unauthorized` si no se envía el token o este es inválido, y `403 Forbidden` si el token es válido pero no se tienen los permisos necesarios.

### Códigos de estado

| Código | Descripción |
|--------|-------------|
| `200 OK` | La petición se completó correctamente |
| `201 Created` | El recurso fue creado correctamente |
| `204 No Content` | La petición se completó correctamente sin contenido en el cuerpo |
| `302 Found` | Redirección (usado en el flujo OAuth de MercadoPago) |
| `400 Bad Request` | La petición tiene datos inválidos o faltantes |
| `401 Unauthorized` | Falta autenticación, el token es inválido o la firma del webhook no valida |
| `402 Payment Required` | El pago fue rechazado |
| `403 Forbidden` | El usuario no tiene permisos para el recurso |
| `404 Not Found` | El recurso no existe |
| `409 Conflict` | El recurso existe pero no está en un estado compatible con la operación |
| `428 Precondition Required` | Falta un requisito previo (por ejemplo, cuenta de MercadoPago vinculada) |
| `500 Internal Server Error` | Error interno del servidor |
| `502 Bad Gateway` | Fallo al comunicarse con MercadoPago |

### Formato de error

Todos los errores de negocio y de validación se devuelven con el siguiente esquema:

```json
{
  "error": "El evento no está disponible para la compra de entradas.",
  "status": 409,
  "timestamp": "2026-10-03T18:30:00"
}
```

En errores de validación (`400`) se agrega el mapa `fields` con el detalle por campo:

```json
{
  "error": "Validation failed",
  "status": 400,
  "timestamp": "2026-10-03T18:30:00",
  "fields": {
    "email": "must be a well-formed email address",
    "password": "Password must be between 8 and 72 characters"
  }
}
```

---

## Autenticación

Prefijo: `/api/v1/auth`

### Registrar usuario

`POST /api/v1/auth/register`

Registra un nuevo usuario (Público).

**Cuerpo de la petición:**

```json
{
  "name": "Juan Pérez",
  "email": "juan@example.com",
  "password": "secret123",
  "dateBirth": "1995-05-20"
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `name` | string | Sí | máx. 100 caracteres | Nombre del usuario |
| `email` | string | Sí | email válido, máx. 254 caracteres | Correo electrónico |
| `password` | string | Sí | entre 8 y 72 caracteres | Contraseña |
| `dateBirth` | string (date) | Sí | debe estar en el pasado, formato `yyyy-MM-dd` | Fecha de nacimiento |

**Respuesta exitosa: `201 Created`**

Devuelve un mensaje de texto plano:

```
Usuario registrado con éxito. Por favor revisa tu correo para verificar tu cuenta.
```

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Faltan campos o no cumplen las validaciones |
| `409 Conflict` | El correo ya está registrado |

> El código de verificación es de 6 dígitos, vence a los 15 minutos y admite un máximo de 5 intentos antes de invalidarse.

### Verificar correo

`POST /api/v1/auth/verify`

Verifica el correo del usuario con el código recibido por correo (Público). Al completarse devuelve el par de tokens, por lo que no hace falta iniciar sesión luego.

**Cuerpo de la petición:**

```json
{
  "email": "juan@example.com",
  "code": "123456"
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `email` | string | Sí | email válido, máx. 254 caracteres | Correo electrónico del usuario |
| `code` | string | Sí | exactamente 6 dígitos | Código de verificación recibido por correo |

**Respuesta exitosa: `200 OK`**

```json
{
  "accessToken": "<jwt-access-token>",
  "refreshToken": "<refresh-token>"
}
```

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Código incorrecto o expirado |
| `401 Unauthorized` | El correo no existe |
| `409 Conflict` | La cuenta ya está verificada |

### Reenviar código de verificación

`POST /api/v1/auth/resend-verification`

Reenvía el código de verificación al correo (Público).

**Cuerpo de la petición:**

```json
{
  "email": "juan@example.com"
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `email` | string | Sí | email válido | Correo electrónico del usuario |

**Respuesta exitosa: `204 No Content`**

**Errores:**

| Código | Causa |
|--------|-------|
| `401 Unauthorized` | El correo no existe |
| `409 Conflict` | La cuenta ya está verificada |

### Iniciar sesión

`POST /api/v1/auth/login`

Inicia sesión con las credenciales (Público).

**Cuerpo de la petición:**

```json
{
  "email": "juan@example.com",
  "password": "secret123"
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `email` | string | Sí | email válido, máx. 254 caracteres | Correo electrónico |
| `password` | string | Sí | máx. 72 caracteres | Contraseña |

**Respuesta exitosa: `200 OK`**

```json
{
  "accessToken": "<jwt-access-token>",
  "refreshToken": "<refresh-token>"
}
```

**Errores:**

| Código | Causa |
|--------|-------|
| `401 Unauthorized` | Credenciales inválidas |
| `403 Forbidden` | El correo no fue verificado |

> Cada inicio de sesión revoca los refresh tokens anteriores del usuario y emite un par nuevo.

### Refrescar token

`POST /api/v1/auth/refresh`

Obtiene un nuevo access token usando el refresh token (Público).

**Cuerpo de la petición:**

```json
{
  "refreshToken": "<refresh-token>"
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `refreshToken` | string | Sí | formato UUID | Token de refresco |

**Respuesta exitosa: `200 OK`**

```json
{
  "accessToken": "<jwt-access-token>",
  "refreshToken": "<refresh-token>"
}
```

> El refresh token no rota: se devuelve el mismo valor y solo se renueva el access token.

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | El token no tiene formato UUID |
| `401 Unauthorized` | El token no existe, fue revocado o expiró |

### Cerrar sesión

`POST /api/v1/auth/logout`

Elimina el refresh token indicado (Requiere autenticación).

**Cuerpo de la petición:**

```json
{
  "refreshToken": "<refresh-token>"
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `refreshToken` | string | Sí | formato UUID | Token de refresco a invalidar |

**Respuesta exitosa: `204 No Content`**

**Errores:**

| Código | Causa |
|--------|-------|
| `401 Unauthorized` | El token no existe |

### Obtener usuario actual

`GET /api/v1/auth/me`

Devuelve la información del usuario autenticado (Requiere autenticación).

**Respuesta exitosa: `200 OK`**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Juan Pérez",
  "email": "juan@example.com",
  "systemRole": "USER",
  "dateBirth": "1995-05-20"
}
```

---

## Eventos

Prefijo: `/api/v1/events`

### Estados del evento

| Estado | Descripción |
|--------|-------------|
| `DRAFT` | Creado por el organizador, aún no visible para la compra |
| `PUBLISHED` | Publicado y disponible para comprar entradas hasta el cierre de la ventana posterior |
| `CANCELLED` | Cancelado por el organizador |
| `FINISHED` | Terminó la ventana de compra posterior al evento (transición automática por tareas programadas) |

La API no modela una fecha de fin (`endDateTime`). La compra se permite mientras
el evento esté `PUBLISHED` y no hayan pasado `event.access-window.after-hours`
horas desde `dateTime` (12 horas por defecto), por lo que se pueden comprar
entradas con cualquier anticipación. Fuera de ese límite responde `409`.

La ventana completa de ingreso va desde `event.access-window.before-hours` horas
antes hasta `event.access-window.after-hours` horas después de `dateTime`
(por defecto, 3 y 12 horas). La validación del ingreso debe aplicar ambos límites; la transición automática a
`FINISHED` ocurre al cerrar la ventana posterior. La desvinculación de Mercado
Pago se bloquea si existen tickets `PENDING` o `ACTIVE` en eventos `PUBLISHED`;
solo los eventos publicados sin tickets `PENDING`, `ACTIVE` ni `USED` vuelven a
`DRAFT`.

### Crear evento

`POST /api/v1/events`

Crea un nuevo evento en estado `DRAFT` y registra al usuario autenticado como organizador (Requiere autenticación).

**Cuerpo de la petición (evento presencial):**

```json
{
  "title": "Conferencia de tecnología",
  "description": "Charla sobre las últimas tendencias",
  "dateTime": "2026-10-15T18:00:00",
  "basePrice": 25.50,
  "eventType": "IN_PERSON",
  "address": "Av. Principal 123",
  "latitude": -34.6037,
  "longitude": -58.3816,
  "capacity": 500,
  "accessLink": null,
  "platform": null,
  "accessInstructions": null
}
```

**Cuerpo de la petición (evento virtual):**

```json
{
  "title": "Conferencia de tecnología",
  "description": "Charla sobre las últimas tendencias",
  "dateTime": "2026-10-15T18:00:00",
  "basePrice": 25.50,
  "eventType": "VIRTUAL",
  "address": null,
  "latitude": null,
  "longitude": null,
  "capacity": null,
  "accessLink": "https://zoom.us/j/123456789",
  "platform": "Zoom",
  "accessInstructions": "Entra 10 minutos antes"
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `title` | string | Sí | máx. 150 caracteres | Título del evento |
| `description` | string | No | máx. 2000 caracteres | Descripción del evento |
| `dateTime` | string (datetime) | Sí | debe estar en el futuro, formato `yyyy-MM-ddTHH:mm:ss` | Fecha y hora del evento |
| `basePrice` | number | Sí | ≥ 0, hasta 8 dígitos enteros y 2 decimales | Precio base del evento |
| `eventType` | string | Sí | `IN_PERSON` o `VIRTUAL` | Tipo de evento |
| `address` | string | Solo `IN_PERSON` | máx. 255 caracteres, no vacío | Dirección |
| `latitude` | number | Solo `IN_PERSON` | entre -90 y 90 | Latitud |
| `longitude` | number | Solo `IN_PERSON` | entre -180 y 180 | Longitud |
| `capacity` | integer | Solo `IN_PERSON` | > 0, máx. 100000 | Capacidad máxima de asistentes |
| `accessLink` | string | Solo `VIRTUAL` | URL `https://`, máx. 500 caracteres, no vacío | Enlace de acceso |
| `platform` | string | Solo `VIRTUAL` | máx. 50 caracteres, no vacío | Plataforma del evento |
| `accessInstructions` | string | Solo `VIRTUAL` | máx. 1000 caracteres | Instrucciones de acceso |

> Para `IN_PERSON` son obligatorios `address`, `latitude`, `longitude` y `capacity`. Para `VIRTUAL` son obligatorios `accessLink` y `platform`.

**Respuesta exitosa: `201 Created`**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Conferencia de tecnología",
  "description": "Charla sobre las últimas tendencias",
  "dateTime": "2026-10-15T18:00:00",
  "status": "DRAFT",
  "basePrice": 25.50,
  "eventType": "IN_PERSON",
  "address": "Av. Principal 123",
  "latitude": -34.6037,
  "longitude": -58.3816,
  "capacity": 500,
  "platform": null
}
```

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Faltan campos obligatorios o las validaciones fallan |
| `401 Unauthorized` | Falta autenticación |

### Publicar evento

`PUT /api/v1/events/{id}/publish`

Publica un evento para que esté disponible (Requiere autenticación, solo el organizador).

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del evento |

**Respuesta exitosa: `200 OK`**

Devuelve el `EventResponse` del evento publicado.

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | La fecha del evento ya pasó |
| `403 Forbidden` | El usuario no es el organizador del evento |
| `404 Not Found` | El evento no existe |
| `409 Conflict` | El evento ya está publicado, o está cancelado/finalizado |
| `428 Precondition Required` | El organizador no tiene una cuenta de MercadoPago vinculada |

### Actualizar evento

`PUT /api/v1/events/{id}`

Actualiza un evento existente (Requiere autenticación, solo el organizador). El tipo de evento no se puede cambiar: los campos enviados se aplican según el tipo ya creado.

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del evento |

**Cuerpo de la petición:**

```json
{
  "title": "Conferencia de tecnología 2026",
  "description": "Descripción actualizada",
  "dateTime": "2026-11-02T18:00:00",
  "basePrice": 30.00,
  "address": "Av. Principal 456",
  "latitude": -34.6037,
  "longitude": -58.3816,
  "capacity": 600,
  "accessLink": null,
  "platform": null,
  "accessInstructions": null
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `title` | string | Sí | máx. 150 caracteres | Título del evento |
| `description` | string | No | máx. 2000 caracteres | Descripción del evento |
| `dateTime` | string (datetime) | Sí | debe estar en el futuro, formato `yyyy-MM-ddTHH:mm:ss` | Fecha y hora del evento |
| `basePrice` | number | Sí | ≥ 0, hasta 8 dígitos enteros y 2 decimales | Precio base del evento |
| `address` | string | Solo `IN_PERSON` | máx. 255 caracteres, no vacío | Dirección |
| `latitude` | number | Solo `IN_PERSON` | entre -90 y 90 | Latitud |
| `longitude` | number | Solo `IN_PERSON` | entre -180 y 180 | Longitud |
| `capacity` | integer | Solo `IN_PERSON` | > 0, máx. 100000 | Capacidad máxima |
| `accessLink` | string | Solo `VIRTUAL` | URL `https://`, máx. 500 caracteres, no vacío | Enlace de acceso |
| `platform` | string | Solo `VIRTUAL` | máx. 50 caracteres, no vacío | Plataforma del evento |
| `accessInstructions` | string | Solo `VIRTUAL` | máx. 1000 caracteres | Instrucciones de acceso |

**Respuesta exitosa: `200 OK`**

Devuelve el `EventResponse` actualizado.

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Validaciones fallidas, o la capacidad es menor a las entradas ya vendidas |
| `403 Forbidden` | El usuario no es el organizador del evento |
| `404 Not Found` | El evento no existe |
| `409 Conflict` | El evento está cancelado/finalizado, o tiene entradas vendidas y se intenta cambiar el precio o la fecha |

### Obtener evento

`GET /api/v1/events/{id}`

Obtiene un evento por su identificador. Los eventos `PUBLISHED` son públicos;
los eventos en cualquier otro estado solo son visibles para su organizador.
Para los demás usuarios, incluidos visitantes anónimos, responde `404` para
no revelar que el evento existe.

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del evento |

**Respuesta exitosa: `200 OK`**

Devuelve el `EventResponse` correspondiente.

**Errores:**

| Código | Causa |
|--------|-------|
| `404 Not Found` | El evento no existe o no es visible para el usuario actual |

### Listar eventos

`GET /api/v1/events`

Obtiene únicamente eventos `PUBLISHED`, ordenados por `dateTime` ascendente
(Público). La respuesta acepta paginación mediante los parámetros `page`,
`size` y `sort`, por ejemplo `?page=0&size=20&sort=dateTime,asc`.

**Respuesta exitosa: `200 OK`**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Conferencia de tecnología",
    "description": "Charla sobre las últimas tendencias",
    "dateTime": "2026-10-15T18:00:00",
    "status": "PUBLISHED",
    "basePrice": 25.50,
    "eventType": "VIRTUAL",
    "address": null,
    "latitude": null,
    "longitude": null,
    "capacity": null,
    "platform": "Zoom"
  }
]
```

### Listar mis eventos

`GET /api/v1/events/mine`

Obtiene los eventos donde el usuario autenticado participa como organizador,
incluidos sus borradores, cancelados y finalizados. La lista se ordena por
`dateTime` descendente y acepta los parámetros `page`, `size` y `sort`.

### Cancelar evento

`PATCH /api/v1/events/{id}/cancel`

Cambia el estado del evento a `CANCELLED` (Requiere autenticación, solo el organizador).

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del evento |

**Respuesta exitosa: `204 No Content`**

**Errores:**

| Código | Causa |
|--------|-------|
| `403 Forbidden` | El usuario no es el organizador del evento |
| `404 Not Found` | El evento no existe |
| `409 Conflict` | El evento está cancelado/finalizado, o ya tiene entradas compradas |

---

## Tickets

Prefijo: `/api/v1/tickets`

### Estados del ticket

| Estado | Descripción |
|--------|-------------|
| `PENDING` | El pago todavía no fue confirmado |
| `ACTIVE` | El pago fue aprobado y el ticket habilita el acceso |
| `USED` | El ticket fue utilizado en el evento |
| `CANCELLED` | El pago fue rechazado o el ticket fue cancelado |

### Comprar ticket

`POST /api/v1/tickets/purchase`

Compra un ticket para un evento (Requiere autenticación).

El flujo reserva el cupo, procesa el pago contra MercadoPago con la cuenta del organizador y devuelve el estado resultante. Para eventos presenciales, el QR y el envío por correo solo se generan si el pago fue aprobado. Para eventos virtuales, el correo de entrega contiene la plataforma, el enlace y las instrucciones de acceso, sin QR.

**Cuerpo de la petición:**

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "cardToken": "tok_card_1a2b3c",
  "installments": 1,
  "payerEmail": "juan@example.com",
  "paymentMethodId": "visa"
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `eventId` | uuid | Sí | — | Identificador del evento |
| `cardToken` | string | Sí | máx. 255 caracteres | Token de la tarjeta del pagador |
| `installments` | integer | Sí | entre 1 y 12 | Número de cuotas |
| `payerEmail` | string | Sí | email válido, máx. 254 caracteres | Correo del pagador |
| `paymentMethodId` | string | Sí | máx. 50 caracteres, solo `[a-z0-9_]` | Identificador del método de pago |

**Respuesta exitosa: `201 Created`**

```json
{
  "ticketId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "approved",
  "statusDetail": "accredited",
  "message": "Pago aprobado. Tu entrada será enviada por email.",
  "qrCodeBase64": "<qr-code-base64>"
}
```

| Campo | Descripción |
|-------|-------------|
| `status` | Estado informado por MercadoPago: `approved`, `rejected`, `cancelled` o `pending` |
| `statusDetail` | Detalle textual del estado informado por MercadoPago |
| `message` | Mensaje legible según el resultado |
| `qrCodeBase64` | QR en base64 para eventos presenciales; `null` para eventos virtuales o mientras el pago no esté aprobado |

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | El evento tiene precio 0 o las validaciones del cuerpo fallan |
| `402 Payment Required` | MercadoPago rechazó el pago |
| `404 Not Found` | El evento no existe |
| `409 Conflict` | El usuario ya tiene una compra pendiente o activa para el evento, no hay cupo disponible, o el evento no está `PUBLISHED` / pasaron más de `event.access-window.after-hours` horas desde `dateTime` |
| `428 Precondition Required` | El organizador no tiene una cuenta de MercadoPago vinculada |
| `502 Bad Gateway` | No se pudo procesar el pago con MercadoPago |

### Listar mis tickets

`GET /api/v1/tickets/my-tickets`

Obtiene los tickets del usuario autenticado (Requiere autenticación). El QR se incluye solo para tickets `ACTIVE` de eventos presenciales. Los datos de acceso de eventos virtuales se envían por correo tras la aprobación del pago.

**Respuesta exitosa: `200 OK`**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "eventTitle": "Conferencia de tecnología",
    "status": "ACTIVE",
    "boughtAt": "2026-09-01T14:30:00",
    "qrCodeBase64": "<qr-code-base64>"
  }
]
```

### Obtener ticket

`GET /api/v1/tickets/{id}`

Obtiene un ticket por su identificador (Requiere autenticación, solo el propietario).

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del ticket |

**Respuesta exitosa: `200 OK`**

Devuelve el `TicketResponse` correspondiente.

**Errores:**

| Código | Causa |
|--------|-------|
| `403 Forbidden` | El ticket pertenece a otro usuario |
| `404 Not Found` | El ticket no existe |

### Reenviar ticket por correo

`POST /api/v1/tickets/{id}/resend-email`

Vuelve a encolar el envío del ticket al correo del titular (Requiere autenticación, solo el propietario). El ticket debe estar `ACTIVE`.

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del ticket |

**Respuesta exitosa: `204 No Content`**

**Errores:**

| Código | Causa |
|--------|-------|
| `403 Forbidden` | El ticket pertenece a otro usuario |
| `404 Not Found` | El ticket no existe |
| `409 Conflict` | El ticket no está activo |

---

## MercadoPago (OAuth del organizador)

Prefijo: `/api/v1/mp`

El organizador debe vincular su cuenta de MercadoPago para poder publicar eventos y recibir el importe de las entradas vendidas.

### Conectar cuenta

`GET /api/v1/mp/connect`

Genera un `state` de un solo uso (válido 10 minutos) y redirige al usuario a la pantalla de autorización de MercadoPago (Requiere autenticación).

**Respuesta exitosa: `302 Found`**

Devuelve la URL de autorización de MercadoPago en el header `Location`:

```
Location: https://auth.mercadopago.com/authorization?client_id=...&response_type=code&platform_id=mp&redirect_uri=...&state=...
```

### Callback de autorización

`GET /api/v1/mp/callback`

Callback de redirección de MercadoPago. Es el único endpoint público del recurso porque lo invoca el proveedor (no el usuario).

**Parámetros de query:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `code` | string | Sí | Código de autorización devuelto por MercadoPago |
| `state` | string | Sí | Valor `state` generado en `/connect` |
| `error` | string | No | Parámetro de error de MercadoPago; si viene presente la operación falla |

**Respuesta exitosa: `200 OK`**

```json
{
  "linked": true,
  "mpEmail": "organizador@example.com",
  "linkedAt": "2026-10-03T18:30:00"
}
```

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | La autorización fue cancelada, o el `state` es inválido/expirado |
| `502 Bad Gateway` | Error al vincular la cuenta con MercadoPago |

### Consultar estado de la cuenta

`GET /api/v1/mp/status`

Indica si el usuario autenticado tiene una cuenta de MercadoPago vinculada (Requiere autenticación).

**Respuesta exitosa: `200 OK`**

Cuenta vinculada:

```json
{
  "linked": true,
  "mpEmail": "organizador@example.com",
  "linkedAt": "2026-10-03T18:30:00"
}
```

Cuenta sin vincular:

```json
{
  "linked": false,
  "mpEmail": null,
  "linkedAt": null
}
```

### Desvincular cuenta

`DELETE /api/v1/mp/disconnect`

Elimina la cuenta de MercadoPago vinculada (Requiere autenticación). La operación
responde `409` si el organizador tiene tickets `PENDING` o `ACTIVE` en cualquier
evento `PUBLISHED`, porque esos pagos todavía pueden requerir el token de
MercadoPago para el webhook o la reconciliación. En ese caso la cuenta no se
elimina.

Si no existen esos tickets abiertos, los eventos `PUBLISHED` sin tickets
`PENDING`, `ACTIVE` ni `USED` vuelven a `DRAFT`. Los eventos que ya vendieron o
utilizaron entradas permanecen `PUBLISHED`, aunque la cuenta se desvincule.

Existe una pequeña ventana de carrera entre la comprobación de tickets abiertos
y la reversión de eventos. Un ticket reservado exactamente durante ese intervalo
podría quedar pendiente sin token del organizador; es una limitación conocida de
esta operación.

**Respuesta exitosa: `204 No Content`**

**Errores:**

| Código | Causa |
|--------|-------|
| `409 Conflict` | El organizador tiene tickets `PENDING` o `ACTIVE` en un evento `PUBLISHED` |
| `428 Precondition Required` | El usuario no tiene una cuenta vinculada |

---

## Webhooks

Prefijo: `/api/v1/webhooks`

### Notificación de pago

`POST /api/v1/webhooks/payments`

Recibe las notificaciones de pago de MercadoPago (Público, validado por firma).

**Headers requeridos:**

| Header | Descripción |
|--------|-------------|
| `x-signature` | Firma de la petición. Formato `ts=<timestamp>,v1=<hmac-sha256-hex>` |
| `x-request-id` | Identificador único de la petición |

**Parámetros de query:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `data.id` | string | Sí | Identificador del pago notificado (debe coincidir con `data.id` del cuerpo) |

**Cuerpo de la petición:**

```json
{
  "action": "payment.updated",
  "type": "payment",
  "data": {
    "id": "1234567890"
  }
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `action` | string | No | — | Acción de la notificación |
| `type` | string | No | si es distinto de `payment` se ignora | Tipo del evento |
| `data` | object | Sí | — | Datos del recurso notificado |
| `data.id` | string | Sí | entre 1 y 20 dígitos | Identificador del pago notificado |

La firma se valida calculando `HMAC-SHA256` con el webhook secret sobre el mensaje:

```
id:<data.id>;request-id:<x-request-id>;ts:<ts>;
```

**Respuesta exitosa: `200 OK`**

Cuerpo vacío. Las notificaciones que no corresponden a pagos, o de pagos ya resueltos, se responden con `200` sin hacer nada.

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Faltan headers, el cuerpo es inválido o `data.id` no coincide con el query |
| `401 Unauthorized` | La firma es inválida o el timestamp tiene más de 5 minutos de antigüedad |
| `409 Conflict` | El pago notificado todavía no está registrado localmente |

---

## Esquemas de respuesta

### AuthResponse

```json
{
  "accessToken": "string",
  "refreshToken": "string"
}
```

### UserResponse

```json
{
  "id": "uuid",
  "name": "string",
  "email": "string",
  "systemRole": "string",
  "dateBirth": "date"
}
```

| Campo | Valores |
|-------|---------|
| `systemRole` | `USER` o `ADMIN` |

### EventResponse

```json
{
  "id": "uuid",
  "title": "string",
  "description": "string",
  "dateTime": "datetime",
  "status": "string",
  "basePrice": "number",
  "eventType": "string",
  "address": "string",
  "latitude": "number",
  "longitude": "number",
  "capacity": "integer",
  "platform": "string"
}
```

| Campo | Valores |
|-------|---------|
| `status` | `DRAFT`, `PUBLISHED`, `CANCELLED` o `FINISHED` |
| `eventType` | `IN_PERSON` o `VIRTUAL` |

Los campos de ubicación (`address`, `latitude`, `longitude`, `capacity`) solo se llenan para eventos de tipo `IN_PERSON`. Para eventos de tipo `VIRTUAL` solo se llena `platform`; `accessLink` y `accessInstructions` se reciben al crear o actualizar, pero no se exponen en las respuestas.

### PurchaseResponse

```json
{
  "ticketId": "uuid",
  "status": "string",
  "statusDetail": "string",
  "message": "string",
  "qrCodeBase64": "string"
}
```

| Campo | Valores |
|-------|---------|
| `status` | `approved`, `rejected`, `cancelled` o `pending` (según MercadoPago) |
| `qrCodeBase64` | `null` si el pago no fue aprobado |

### TicketResponse

```json
{
  "id": "uuid",
  "eventId": "uuid",
  "eventTitle": "string",
  "status": "string",
  "boughtAt": "datetime",
  "qrCodeBase64": "string"
}
```

| Campo | Valores |
|-------|---------|
| `status` | `PENDING`, `ACTIVE`, `USED` o `CANCELLED` |
| `qrCodeBase64` | `null` si el ticket no está `ACTIVE` |

### MpAccountStatusResponse

```json
{
  "linked": "boolean",
  "mpEmail": "string",
  "linkedAt": "datetime"
}
```

`mpEmail` y `linkedAt` son `null` cuando `linked` es `false`.

### ErrorResponse

```json
{
  "error": "string",
  "status": "integer",
  "timestamp": "datetime",
  "fields": {}
}
```

`fields` solo aparece en errores de validación y mapea cada campo con su mensaje de error.
