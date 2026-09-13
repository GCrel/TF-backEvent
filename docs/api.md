# Documentación de la API - TF-backEvent

## Información general

- **Base URL:** `http://localhost:8080`
- **Formato de datos:** JSON
- **Autenticación:** Bearer Token (JWT)

### Autenticación

La mayoría de los endpoints requieren un token de acceso en el header:

```
Authorization: Bearer <access-token>
```

Los endpoints protegidos devuelven `401 Unauthorized` si no se envía el token o este es inválido, y `403 Forbidden` si el token es válido pero no se tienen los permisos necesarios.

### Códigos de estado

| Código | Descripción |
|--------|-------------|
| `200 OK` | La petición se completó correctamente |
| `201 Created` | El recurso fue creado correctamente |
| `204 No Content` | La petición se completó correctamente sin contenido en el cuerpo |
| `400 Bad Request` | La petición tiene datos inválidos o faltantes |
| `401 Unauthorized` | Falta autenticación o el token es inválido |
| `403 Forbidden` | El usuario no tiene permisos para el recurso |
| `404 Not Found` | El recurso no existe |
| `500 Internal Server Error` | Error interno del servidor |

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

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `name` | string | Nombre del usuario |
| `email` | string | Correo electrónico |
| `password` | string | Contraseña |
| `dateBirth` | string (date) | Fecha de nacimiento, formato `yyyy-MM-dd` |

**Respuesta exitosa: `201 Created`**

```json
"Usuario registrado con éxito. Por favor revisa tu correo para verificar tu cuenta."
```

### Verificar correo

`POST /api/v1/auth/verify`

Verifica el correo del usuario con el código enviado (Público).

**Cuerpo de la petición:**

```json
{
  "email": "juan@example.com",
  "code": "123456"
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `email` | string | Correo electrónico del usuario |
| `code` | string | Código de verificación recibido por correo |

**Respuesta exitosa: `200 OK`**

```json
{
  "accessToken": "<jwt-access-token>",
  "refreshToken": "<jwt-refresh-token>"
}
```

### Reenviar código de verificación

`POST /api/v1/auth/resend-verification`

Reenvía el código de verificación al correo (Público).

**Cuerpo de la petición:**

```json
{
  "email": "juan@example.com"
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `email` | string | Correo electrónico del usuario |

**Respuesta exitosa: `204 No Content`**

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

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `email` | string | Correo electrónico |
| `password` | string | Contraseña |

**Respuesta exitosa: `200 OK`**

```json
{
  "accessToken": "<jwt-access-token>",
  "refreshToken": "<jwt-refresh-token>"
}
```

### Refrescar token

`POST /api/v1/auth/refresh`

Obtiene un nuevo par de tokens usando el refresh token (Público).

**Cuerpo de la petición:**

```json
{
  "refreshToken": "<jwt-refresh-token>"
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `refreshToken` | string | Token de refresco |

**Respuesta exitosa: `200 OK`**

```json
{
  "accessToken": "<jwt-access-token>",
  "refreshToken": "<jwt-refresh-token>"
}
```

### Cerrar sesión

`POST /api/v1/auth/logout`

Invalida el refresh token (Requiere autenticación).

**Cuerpo de la petición:**

```json
{
  "refreshToken": "<jwt-refresh-token>"
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `refreshToken` | string | Token de refresco a invalidar |

**Respuesta exitosa: `204 No Content`**

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

### Crear evento

`POST /api/v1/events`

Crea un nuevo evento (Requiere autenticación).

**Cuerpo de la petición:**

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

Para eventos virtuales, `eventType` debe ser `"VIRTUAL"` y se usan los campos `accessLink`, `platform` y `accessInstructions`. Los campos de la tabla son:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `title` | string | Título del evento |
| `description` | string | Descripción del evento |
| `dateTime` | string (datetime) | Fecha y hora del evento, formato `yyyy-MM-ddTHH:mm:ss` |
| `basePrice` | number | Precio base del evento |
| `eventType` | string | Tipo de evento: `IN_PERSON` o `VIRTUAL` |
| `address` | string | Dirección (solo IN_PERSON) |
| `latitude` | number | Latitud (solo IN_PERSON) |
| `longitude` | number | Longitud (solo IN_PERSON) |
| `capacity` | integer | Capacidad máxima de asistentes (solo IN_PERSON) |
| `accessLink` | string | Enlace de acceso (solo VIRTUAL) |
| `platform` | string | Plataforma del evento (solo VIRTUAL) |
| `accessInstructions` | string | Instrucciones de acceso (solo VIRTUAL) |

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
  "accessLink": null,
  "platform": null,
  "accessInstructions": null
}
```

### Publicar evento

`PUT /api/v1/events/{id}/publish`

Publica un evento para que esté disponible (Requiere autenticación, solo el organizador).

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del evento |

**Respuesta exitosa: `200 OK`**

Devuelve el `EventResponse` del evento publicado.

### Actualizar evento

`PUT /api/v1/events/{id}`

Actualiza un evento existente (Requiere autenticación, solo el organizador).

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

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `title` | string | Título del evento |
| `description` | string | Descripción del evento |
| `dateTime` | string (datetime) | Fecha y hora del evento |
| `basePrice` | number | Precio base del evento |
| `address` | string | Dirección (solo IN_PERSON) |
| `latitude` | number | Latitud (solo IN_PERSON) |
| `longitude` | number | Longitud (solo IN_PERSON) |
| `capacity` | integer | Capacidad máxima (solo IN_PERSON) |
| `accessLink` | string | Enlace de acceso (solo VIRTUAL) |
| `platform` | string | Plataforma del evento (solo VIRTUAL) |
| `accessInstructions` | string | Instrucciones de acceso (solo VIRTUAL) |

**Respuesta exitosa: `200 OK`**

Devuelve el `EventResponse` actualizado.

### Obtener evento

`GET /api/v1/events/{id}`

Obtiene un evento por su identificador (Público).

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del evento |

**Respuesta exitosa: `200 OK`**

Devuelve el `EventResponse` correspondiente.

### Listar eventos

`GET /api/v1/events`

Obtiene todos los eventos (Público).

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
    "accessLink": "https://zoom.us/j/123456789",
    "platform": "Zoom",
    "accessInstructions": "Entra 10 minutos antes"
  }
]
```

### Cancelar evento

`PATCH /api/v1/events/{id}/cancel`

Cancela un evento (Requiere autenticación, solo el organizador).

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del evento |

**Respuesta exitosa: `204 No Content`**

---

## Tickets

Prefijo: `/api/v1/tickets`

### Comprar ticket

`POST /api/v1/tickets/purchase`

Compra un ticket para un evento (Requiere autenticación).

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

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `eventId` | uuid | Identificador del evento |
| `cardToken` | string | Token de la tarjeta del pagador |
| `installments` | integer | Número de cuotas |
| `payerEmail` | string | Correo del pagador |
| `paymentMethodId` | string | Identificador del método de pago |

**Respuesta exitosa: `201 Created`**

```json
{
  "ticketId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "CONFIRMED",
  "message": "Ticket confirmado",
  "qrCodeBase64": "<qr-code-base64>"
}
```

### Listar mis tickets

`GET /api/v1/tickets/my-tickets`

Obtiene los tickets del usuario autenticado (Requiere autenticación).

**Respuesta exitosa: `200 OK`**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "eventTitle": "Conferencia de tecnología",
    "status": "CONFIRMED",
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

### Reenviar ticket por correo

`POST /api/v1/tickets/{id}/resend-email`

Reenvía el ticket al correo del titular (Requiere autenticación, solo el propietario).

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador del ticket |

**Respuesta exitosa: `204 No Content`**

---

## Webhooks

Prefijo: `/api/v1/webhooks`

### Notificación de pago

`POST /api/v1/webhooks/payments`

Recibe las notificaciones de pago del proveedor de pagos (Público, validado por firma).

**Headers requeridos:**

| Header | Descripción |
|--------|-------------|
| `x-signature` | Firma de la petición para validar la autenticidad |
| `x-request-id` | Identificador único de la petición |

**Cuerpo de la petición:**

```json
{
  "action": "payment.created",
  "type": "payment",
  "data": {
    "id": "1234567890"
  }
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `action` | string | Acción de la notificación |
| `type` | string | Tipo del evento |
| `data.id` | string | Identificador del recurso notificado |

**Respuesta exitosa: `200 OK`**

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
  "accessLink": "string",
  "platform": "string",
  "accessInstructions": "string"
}
```

Los campos de ubicación (`address`, `latitude`, `longitude`, `capacity`) solo se llenan para eventos de tipo `IN_PERSON`. Los campos virtuales (`accessLink`, `platform`, `accessInstructions`) solo se llenan para eventos de tipo `VIRTUAL`.

### PurchaseResponse

```json
{
  "ticketId": "uuid",
  "status": "string",
  "message": "string",
  "qrCodeBase64": "string"
}
```

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