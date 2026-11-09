# Documentación de la API - TF-backEvent

## Información general

- **Base URL:** `http://localhost:8080` (Nginx, con Docker Compose). El backend también responde directo en `http://localhost:8085`, pero las imágenes (`/api/v1/images/...`) solo las sirve Nginx.
- **Formato de datos:** JSON
- **Autenticación:** cookies `HttpOnly` con JWT (ver [Autenticación](#autenticación))
- **Documentación interactiva (perfil `dev`):** `http://localhost:8085/swagger-ui.html` (`/v3/api-docs` para el JSON OpenAPI)

> En el perfil `prod` Swagger está deshabilitado (`springdoc.swagger-ui.enabled=false`).

### Autenticación

La sesión viaja en **cookies `HttpOnly`** que el backend fija al iniciar sesión; el
frontend nunca ve ni guarda los tokens (no usar `localStorage` ni `sessionStorage`).

| Cookie | Contenido | Se envía a | Vida |
|--------|-----------|------------|------|
| `access_token` | JWT de acceso | `/api/**` | `JWT_ACCESS_TOKEN_EXPIRATION` (30 min) |
| `refresh_token` | Token de refresco | solo `/api/v1/auth/**` | `JWT_REFRESH_TOKEN_EXPIRATION` (7 días) |

Las cookies son `HttpOnly`, `Secure` y `SameSite=Strict` (configurables, ver
[`setup.md`](setup.md#variables-de-entorno)). El header `Authorization` **no** se acepta.

**Qué debe hacer el frontend:**

- Enviar **todas** las peticiones a la API con credenciales (`fetch(..., { credentials: 'include' })`,
  o `withCredentials: true` en Angular `HttpClient`). Sin esto el navegador no manda las cookies
  en peticiones entre orígenes.
- Después de `login`, `verify` o un cambio de email/contraseña (todos responden `204` sin
  cuerpo) pedir `GET /api/v1/auth/me` para obtener al usuario.
- Ante un `401`, llamar una vez a `POST /api/v1/auth/refresh` y reintentar la petición; si
  también da `401`, mandar al usuario al login.
- Para saber si hay sesión al abrir la app, llamar a `GET /api/v1/auth/me`: `200` hay sesión,
  `401` no la hay.

Los endpoints protegidos devuelven `401 Unauthorized` si no hay cookie de acceso o es inválida, y `403 Forbidden` si el token es válido pero no se tienen los permisos necesarios. Las peticiones desde un origen no listado en `CORS_ALLOWED_ORIGINS` se rechazan con `403`, también las que no son preflight.

### Códigos de estado

| Código | Descripción |
|--------|-------------|
| `200 OK` | La petición se completó correctamente |
| `201 Created` | El recurso fue creado correctamente |
| `204 No Content` | La petición se completó correctamente sin contenido en el cuerpo |
| `302 Found` | Redirección (callback OAuth de MercadoPago hacia el frontend) |
| `400 Bad Request` | La petición tiene datos inválidos o faltantes |
| `401 Unauthorized` | Falta autenticación, el token es inválido o la firma del webhook no valida |
| `402 Payment Required` | El pago fue rechazado |
| `403 Forbidden` | El usuario no tiene permisos para el recurso |
| `404 Not Found` | El recurso no existe |
| `409 Conflict` | El recurso existe pero no está en un estado compatible con la operación |
| `413 Payload Too Large` | El archivo subido supera el tamaño máximo |
| `415 Unsupported Media Type` | El archivo subido no tiene un formato aceptado |
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

Algunos errores de negocio agregan el mapa `details` con datos extra. Por ahora solo
lo usa el `409` de ticket ya utilizado (ver [Validar ingreso presencial](#validar-ingreso-presencial)).

### Paginación

Los listados de eventos (`GET /api/v1/events`, `GET /api/v1/events/mine` y
`GET /api/v1/access/my-events`) aceptan los parámetros `page` (desde `0`) y `size`
(por defecto `20`), por ejemplo `?page=0&size=20`, y devuelven una página:

```json
{
  "content": [ /* elementos de la página */ ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

| Campo | Descripción |
|-------|-------------|
| `content` | Elementos de la página pedida |
| `page.size` | Tamaño de página usado |
| `page.number` | Número de la página devuelta (desde `0`) |
| `page.totalElements` | Total de elementos en todas las páginas |
| `page.totalPages` | Total de páginas; la página es la última si `number + 1 >= totalPages` |

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

Verifica el correo del usuario con el código recibido por correo (Público). Al completarse inicia la sesión (fija las cookies), por lo que no hace falta iniciar sesión luego.

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

**Respuesta exitosa: `204 No Content`**, con las cookies `access_token` y `refresh_token` en `Set-Cookie`.

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Código incorrecto, vencido o invalidado tras 5 intentos fallidos (en los dos últimos casos hay que pedir un código nuevo), o el cuerpo no cumple las validaciones |
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

Si existe una cuenta pendiente de verificación con ese email, genera un código nuevo
(15 minutos), reinicia el contador de intentos fallidos y lo envía por correo.

La respuesta es **siempre `204`**, exista o no la cuenta y esté o no verificada,
para no revelar qué emails están registrados. El frontend debería mostrar un
mensaje genérico (por ejemplo, "Si el email está registrado y pendiente de
verificación, te enviamos un código nuevo").

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | El email falta o no tiene formato válido |

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

**Respuesta exitosa: `204 No Content`**, con las cookies `access_token` y `refresh_token` en `Set-Cookie`. Para obtener al usuario, llamar a `GET /api/v1/auth/me`.

**Errores:**

| Código | Causa |
|--------|-------|
| `401 Unauthorized` | Credenciales inválidas (email inexistente o contraseña incorrecta) |
| `403 Forbidden` | La contraseña es correcta pero el correo no fue verificado |

> La contraseña se valida antes que el estado de la cuenta: con una contraseña
> incorrecta siempre se responde `401`, sin revelar si la cuenta existe o está
> pendiente de verificación.
>
> Cada inicio de sesión elimina los refresh tokens anteriores del usuario y emite un par nuevo.

### Refrescar token

`POST /api/v1/auth/refresh`

Renueva el access token usando la cookie `refresh_token` (Público). No lleva cuerpo: el
navegador envía la cookie solo a `/api/v1/auth`.

**Respuesta exitosa: `204 No Content`**, con la cookie `access_token` nueva en `Set-Cookie`.

> El refresh token no rota: la cookie `refresh_token` no cambia y solo se renueva el access token.

**Errores:**

| Código | Causa |
|--------|-------|
| `401 Unauthorized` | Falta la cookie, el token no existe (por ejemplo, tras un logout o un nuevo login) o expiró |

### Cerrar sesión

`POST /api/v1/auth/logout`

Elimina el refresh token de la cookie `refresh_token` y borra las dos cookies de sesión
(Público, para poder cerrar sesión aunque el access token haya vencido). No lleva cuerpo.

**Respuesta exitosa: `204 No Content`**, con las cookies vaciadas (`Max-Age=0`). Es
idempotente: responde igual si no había sesión o el token ya no existía.

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
  "dateBirth": "1995-05-20",
  "avatarUrl": "/api/v1/images/3f2a9c1e-7b4d-4e8a-9c55-1d2f6a8b0e11.webp"
}
```

`avatarUrl` es una ruta relativa que sirve Nginx (ver [Descargar una imagen](#descargar-una-imagen)),
o `null` si el usuario no tiene avatar.

### Editar perfil

`PATCH /api/v1/auth/me`

Cambia el nombre del usuario autenticado (Requiere autenticación).

```json
{
  "name": "Juan Carlos Pérez"
}
```

| Campo | Tipo | Descripción |
|------|------|-------------|
| `name` | string | Obligatorio, no vacío, máximo 100 caracteres |

Devuelve `200 OK` con el `UserResponse` actualizado.

### Cambiar el email

El cambio tiene dos pasos y exige la contraseña actual más un código que se envía al
**email nuevo**, para comprobar que la persona tiene acceso a él. El email actual
sigue vigente hasta confirmar.

`POST /api/v1/auth/me/email/request`

```json
{
  "newEmail": "nuevo@example.com",
  "currentPassword": "mi-clave-actual"
}
```

Devuelve `204 No Content` y envía un código de 6 dígitos (vence a los 15 minutos) al
email nuevo. Un pedido nuevo reemplaza al anterior.

| Código | Causa |
|--------|-------|
| `400 Bad Request` | El email es inválido o igual al actual |
| `403 Forbidden` | La contraseña actual es incorrecta |
| `409 Conflict` | Ya existe una cuenta con ese email |

`POST /api/v1/auth/me/email/confirm`

```json
{
  "code": "123456"
}
```

Devuelve `204 No Content` y fija cookies de sesión nuevas: el access token lleva el email
como identidad, así que el anterior deja de servir, y se cierran las demás sesiones (se
borran los refresh tokens). El front solo debe volver a pedir `GET /auth/me`.

| Código | Causa |
|--------|-------|
| `400 Bad Request` | No hay un cambio de email pendiente, el código es incorrecto o venció. A los 5 intentos fallidos el código se invalida y hay que pedir otro |
| `409 Conflict` | Otra cuenta tomó ese email entre el pedido y la confirmación |

### Cambiar la contraseña

Mismo esquema: contraseña actual más un código, que en este caso se envía al email
**actual**.

`POST /api/v1/auth/me/password/request`

```json
{
  "currentPassword": "mi-clave-actual"
}
```

Devuelve `204 No Content`. Responde `403 Forbidden` si la contraseña actual es
incorrecta.

`POST /api/v1/auth/me/password/confirm`

```json
{
  "code": "123456",
  "newPassword": "mi-clave-nueva"
}
```

| Campo | Tipo | Descripción |
|------|------|-------------|
| `code` | string | Código de 6 dígitos |
| `newPassword` | string | Entre 8 y 72 caracteres, distinta de la actual |

Devuelve `204 No Content`, fija cookies de sesión nuevas y cierra las demás sesiones (se
borran los refresh tokens). Responde `400 Bad Request` si no hay un cambio pendiente, el
código es incorrecto o venció, o la contraseña nueva es igual a la actual.

Un código emitido para el cambio de email no sirve para confirmar el de contraseña,
ni al revés.

Los access tokens ya emitidos en otros dispositivos siguen siendo válidos hasta que
expiren, salvo tras un cambio de email, que los invalida de inmediato.

### Avatar

`PUT /api/v1/auth/me/avatar` (`multipart/form-data`, campo `file`)

Sube o reemplaza el avatar (Requiere autenticación). Acepta JPEG, PNG o WebP, con
hasta **5 MB** (`413 Payload Too Large` si lo supera). Devuelve `200 OK` con el
`UserResponse` (incluye `avatarUrl`). Responde `400 Bad Request` si la imagen está
vacía o no es de un tipo permitido.

`DELETE /api/v1/auth/me/avatar`

Quita el avatar. Devuelve `204 No Content`, también si no había uno.

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
(por defecto, 3 y 12 horas). La validación del ingreso aplica ambos límites; la
transición automática a `FINISHED` ocurre al cerrar la ventana posterior.

Un evento con `basePrice` igual a `0` es **gratuito**: se puede publicar sin cuenta
de MercadoPago y sus entradas se emiten sin pago (ver [Comprar entradas](#comprar-entradas)).

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
| `basePrice` | number | Sí | ≥ 0, hasta 8 dígitos enteros y 2 decimales | Precio por entrada; `0` = evento gratuito |
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
  "availableTickets": 500,
  "platform": null,
  "isOrganizer": true,
  "organizerName": "Ana García",
  "imageUrl": null
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
| `428 Precondition Required` | El evento tiene precio y el organizador no tiene una cuenta de MercadoPago vinculada |

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
| `428 Precondition Required` | El evento está publicado, el nuevo precio es mayor que 0 y el organizador no tiene cuenta de MercadoPago vinculada |

### Obtener evento

`GET /api/v1/events/{id}`

Obtiene un evento por su identificador. Los eventos `PUBLISHED` son públicos;
los eventos en cualquier otro estado solo son visibles para su organizador.
Para los demás usuarios, incluidos visitantes anónimos, responde `404` para
no revelar que el evento existe.

La respuesta incluye `isOrganizer`: `true` si quien consulta es el organizador
del evento y `false` en cualquier otro caso (incluido el acceso anónimo), para
decidir entre mostrar "Editar" o "Comprar".

Si el evento es `VIRTUAL` y quien consulta es su organizador, la respuesta incluye
además `accessLink` y `accessInstructions` (por ejemplo, para precargar el
formulario de edición). Para el resto de los usuarios esos campos no aparecen.

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
(Público). La respuesta está [paginada](#paginación).

**Parámetros de consulta:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `eventType` | string | No | `IN_PERSON` o `VIRTUAL`. Sin el parámetro se devuelven ambos tipos; un valor distinto responde `400 Bad Request`. |
| `q` | string | No | Texto a buscar en el título o la descripción, sin distinguir mayúsculas. Se busca literal (`%` y `_` no son comodines). Vacío equivale a no enviarlo. |
| `from` | date (`YYYY-MM-DD`) | No | Eventos desde ese día inclusive. |
| `to` | date (`YYYY-MM-DD`) | No | Eventos hasta ese día inclusive. |
| `page`, `size` | integer | No | Ver [Paginación](#paginación). |

Los filtros se combinan entre sí. Una fecha con otro formato, o `from` posterior a
`to`, responde `400 Bad Request`.

Ejemplo: `GET /api/v1/events?eventType=VIRTUAL&q=java&from=2026-11-01&to=2026-11-30&page=0&size=20`

**Respuesta exitosa: `200 OK`**

```json
{
  "content": [
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
      "availableTickets": null,
      "platform": "Zoom",
      "organizerName": "Ana García",
      "imageUrl": "/api/v1/images/3f2a9c1e-1b2c-4d5e-8f90-0123456789ab.webp"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Listar mis eventos

`GET /api/v1/events/mine`

Obtiene los eventos donde el usuario autenticado participa como organizador,
incluidos sus borradores, cancelados y finalizados. Se ordena por `dateTime`
descendente (los más recientes primero). Requiere autenticación.

**Parámetros de consulta:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `status` | string | No | `DRAFT`, `PUBLISHED`, `CANCELLED` o `FINISHED`. Sin el parámetro se devuelven todos los estados; un valor distinto responde `400 Bad Request`. |
| `page`, `size` | integer | No | Ver [Paginación](#paginación). |

Ejemplo: `GET /api/v1/events/mine?status=DRAFT&page=0&size=20`

**Respuesta exitosa: `200 OK`**

Una [página](#paginación) de `EventResponse`, con el mismo formato que
[Listar eventos](#listar-eventos). Como en todo listado, `accessLink`,
`accessInstructions` e `isOrganizer` se omiten; para editar un evento se usa
[Obtener evento](#obtener-evento).

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

### Subir o reemplazar la imagen del evento

`PUT /api/v1/events/{id}/image`

Guarda la imagen de portada del evento; si ya tenía una, la reemplaza (Requiere
autenticación, solo el organizador). El evento debe existir primero: el frontend
lo crea y después sube la imagen.

El cuerpo es `multipart/form-data` con el archivo en el campo `file`. Se aceptan
JPEG, PNG y WebP de hasta **5 MB**; el formato se reconoce por el contenido del
archivo, no por su nombre ni por el `Content-Type` declarado. SVG no se acepta. Se
recomienda redimensionar y comprimir (preferentemente a WebP) antes de subir.

```bash
curl -X PUT http://localhost:8080/api/v1/events/{id}/image \
  -b "access_token=<jwt>" \
  -F "file=@portada.webp"
```

**Respuesta exitosa: `200 OK`**

Devuelve el `EventResponse` con `imageUrl` actualizado, por ejemplo
`"/api/v1/images/3f2a9c1e-1b2c-4d5e-8f90-0123456789ab.webp"`.

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Falta el campo `file` o el archivo está vacío |
| `403 Forbidden` | El usuario no es el organizador del evento |
| `404 Not Found` | El evento no existe |
| `409 Conflict` | El evento está cancelado o finalizado |
| `413 Payload Too Large` | El archivo supera 5 MB |
| `415 Unsupported Media Type` | El archivo no es JPEG, PNG ni WebP |

### Quitar la imagen del evento

`DELETE /api/v1/events/{id}/image`

Quita la imagen de portada (Requiere autenticación, solo el organizador). Si el
evento no tenía imagen, no hace nada.

**Respuesta exitosa: `204 No Content`**

**Errores:** los mismos `403`, `404` y `409` que al subirla.

### Descargar una imagen

`GET /api/v1/images/{key}`

Público. Lo atiende **Nginx** directamente desde el volumen de imágenes, sin pasar
por el backend: la URL se arma con el `imageUrl` del evento (relativo, el frontend
le antepone la URL base). Solo responde a claves con el formato
`<uuid>.(webp|jpg|png)`; cualquier otra ruta devuelve `404`.

Las respuestas incluyen `Cache-Control: public, max-age=31536000, immutable` (al
reemplazar una imagen cambia su URL) y `X-Content-Type-Options: nosniff`.

### Sectores sobre un plano

Un evento `IN_PERSON` puede dividirse en **sectores** (VIP, General…), cada uno con
nombre, precio y capacidad propios. El **plano** del recinto es **opcional**: si el
organizador lo sube, puede dibujar cada sector sobre él (`points`); si no tiene plano,
los sectores funcionan igual, sin geometría. El backend no procesa la imagen: guarda
la foto y los números que envía el frontend. Un evento sin sectores funciona como
siempre, con `basePrice` y `capacity` únicos.

Flujo: crear el evento (borrador) → [subir el plano] → guardar los sectores → publicar.

Con sectores:

- `capacity` del evento pasa a ser la **suma** de las capacidades de los sectores y
  `basePrice` el **precio mínimo** (el "desde $X" de las tarjetas). Ambos se ignoran
  en `PUT /api/v1/events/{id}`: se cambian editando los sectores.
- El evento se considera **pago** si **algún** sector tiene precio: publicarlo exige
  MercadoPago vinculado aunque `basePrice` sea 0.
- La compra exige `sectorId`; el precio y el cupo son los del sector.

#### Subir o reemplazar el plano

`PUT /api/v1/events/{id}/layout-image`

Igual que [la portada](#subir-o-reemplazar-la-imagen-del-evento): multipart con el
archivo en `file`, JPEG/PNG/WebP de hasta 5 MB, solo el organizador, evento no
cancelado ni finalizado. Solo para eventos `IN_PERSON` (`400` en virtuales). Devuelve
el `EventResponse` con `layoutImageUrl` actualizado. Reemplazar el plano conserva los
sectores (si el recinto cambió, conviene redibujarlos).

#### Quitar el plano

`DELETE /api/v1/events/{id}/layout-image`

Quita el plano. Los sectores se conservan, con su precio, cupo y entradas; sus
`points` quedan guardados, pero sin plano el frontend no tiene sobre qué dibujarlos.
Responde `204`.

#### Guardar los sectores

`PUT /api/v1/events/{id}/sectors`

Reemplaza la lista completa de sectores en una sola operación: los que traen `id` se
actualizan, los que no lo traen se crean y los que faltan se borran. No requiere
plano. Solo el organizador, evento `IN_PERSON` no cancelado ni finalizado.

```json
{
  "sectors": [
    {
      "id": "0b6f3a2e-1c4d-4e5f-9a8b-7c6d5e4f3a2b",
      "name": "VIP",
      "price": 15000.00,
      "capacity": 100,
      "color": "#e63946",
      "points": [
        { "x": 0.10, "y": 0.20 }, { "x": 0.40, "y": 0.20 },
        { "x": 0.40, "y": 0.50 }, { "x": 0.10, "y": 0.50 }
      ]
    },
    {
      "name": "General",
      "price": 5000.00,
      "capacity": 400,
      "points": [ { "x": 0.5, "y": 0.2 }, { "x": 0.9, "y": 0.2 }, { "x": 0.7, "y": 0.8 } ]
    }
  ]
}
```

| Campo | Tipo | Requerido | Restricciones |
|-------|------|-----------|---------------|
| `sectors` | array | Sí | Máximo 30. Vacío quita todos los sectores |
| `id` | uuid | No | Sector existente del evento; sin `id` se crea uno nuevo |
| `name` | string | Sí | Máx. 50 caracteres, único en el evento sin distinguir mayúsculas |
| `price` | number | Sí | ≥ 0, hasta 8 enteros y 2 decimales |
| `capacity` | integer | Sí | Entre 1 y 100000 |
| `color` | string | No | `#RRGGBB` |
| `points` | array | No | Polígono de 3 a 20 vértices (un rectángulo son 4). Se omite si el evento no tiene plano |
| `points[].x`, `points[].y` | number | Sí | Entre 0 y 1: posición **normalizada** respecto del ancho y alto del plano, no píxeles |

No se valida que los sectores se superpongan.

Un sector con entradas vendidas o pendientes de pago **no se puede borrar**, su
`price` **no puede cambiar** y su `capacity` no puede bajar de lo ya ocupado; su
nombre, color y vértices sí se pueden editar. Tampoco se puede pasar de "sin
sectores" a "con sectores" (ni al revés) si el evento ya tiene entradas vendidas.

**Respuesta exitosa: `200 OK`** — la lista de [`SectorResponse`](#sectorresponse)
ordenada por nombre.

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Validaciones del cuerpo, nombre repetido, `id` ajeno al evento o repetido, o evento virtual |
| `403 Forbidden` | El usuario no es el organizador del evento |
| `404 Not Found` | El evento no existe |
| `409 Conflict` | Evento cancelado/finalizado, o el cambio afecta entradas vendidas |
| `428 Precondition Required` | El evento está publicado, algún sector tiene precio y no hay MercadoPago vinculado |

---

## Controladores de acceso

Prefijo: `/api/v1/events/{eventId}/controllers`

Los siguientes endpoints requieren autenticación y solo pueden ser utilizados
por el organizador del evento:

### Asignar controlador

`POST /api/v1/events/{eventId}/controllers`

Asigna como controlador a un usuario habilitado mediante su email. Devuelve
`201 Created` con su identificador, nombre y email.

Responde `404 Not Found` si el email no corresponde a ninguna cuenta o si la
cuenta todavía no está verificada. El campo `error` distingue cada caso:

| Situación | `error` |
|-----------|---------|
| No existe ninguna cuenta con ese email | `No hay ningún usuario con ese email.` |
| La cuenta existe pero no está verificada | `El usuario todavía no verificó su cuenta.` |

Responde `400 Bad Request` si el evento es `VIRTUAL`: los controladores validan
entradas en el acceso de los eventos presenciales.

**Cuerpo de la petición:**

```json
{
  "email": "controlador@example.com"
}
```

| Campo | Tipo | Descripción |
|------|------|-------------|
| `email` | string | Email del usuario habilitado, obligatorio, máximo 254 caracteres |

**Respuesta `201 Created`:**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "name": "Ana Control",
  "email": "controlador@example.com"
}
```

| Campo | Tipo | Descripción |
|------|------|-------------|
| `userId` | uuid | Identificador del controlador |
| `name` | string | Nombre del controlador |
| `email` | string | Email normalizado del controlador |

### Listar controladores

`GET /api/v1/events/{eventId}/controllers`

Lista los usuarios asignados como controladores. Devuelve `200 OK`.

### Quitar controlador

`DELETE /api/v1/events/{eventId}/controllers/{userId}`

Quita un controlador del evento. Devuelve `204 No Content`, o `404 Not Found` si
el usuario no estaba asignado como controlador.

La asignación falla con `404 Not Found` si el evento, el usuario no existe o el
usuario no está habilitado, con `400 Bad Request` si el organizador intenta
asignarse a sí mismo, con `409 Conflict` si ya está asignado o el evento está
cancelado o finalizado, y con `403 Forbidden` si quien solicita la operación no
es el organizador.

---

## Control de acceso

Prefijo: `/api/v1/access`

### Validar ingreso presencial

`POST /api/v1/access/validate-qr`

Valida el QR de un ticket presencial. Requiere autenticación como organizador o
controlador del evento y solo funciona durante la ventana de ingreso configurada
(`event.access-window.before-hours` a `event.access-window.after-hours`).
La validación registra el primer acceso, marca el ticket como `USED` y guarda
quién lo validó. La actualización es atómica, por lo que dos escaneos
simultáneos no permiten ingresar dos veces.

**Cuerpo de la petición:**

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "qrCode": "550e8400-e29b-41d4-a716-446655440001"
}
```

| Campo | Tipo | Descripción |
|------|------|-------------|
| `eventId` | uuid | Evento del ticket, obligatorio |
| `qrCode` | string | UUID del ticket codificado en el QR, obligatorio, máximo 64 caracteres |

**Respuesta `200 OK`:**

```json
{
  "ticketId": "550e8400-e29b-41d4-a716-446655440001",
  "attendeeName": "Juan Pérez",
  "eventTitle": "Conferencia de tecnología",
  "sectorName": "VIP",
  "validatedAt": "2026-10-15T17:05:00"
}
```

| Campo | Tipo | Descripción |
|------|------|-------------|
| `ticketId` | uuid | Ticket validado |
| `attendeeName` | string | Nombre del asistente |
| `eventTitle` | string | Título del evento |
| `validatedAt` | date-time | Momento de la validación |

Devuelve `200 OK` con el ticket, el asistente, el evento y la hora de
validación. Responde `409 Conflict` si el evento está fuera de su ventana, el
ticket no es válido o ya fue utilizado; `400 Bad Request` si el QR no contiene
un UUID válido, no pertenece al evento o el evento es virtual; `404 Not Found`
si el evento o el ticket no existen; y `403 Forbidden` si el usuario no es
organizador ni controlador.

Si el ticket ya fue utilizado, el `409` indica cuándo y quién lo validó, para
resolver disputas en la puerta:

```json
{
  "error": "Ticket fue usado: 15/10/2026 17:05 por Ana Control",
  "status": 409,
  "timestamp": "2026-10-15T17:20:00",
  "details": {
    "validatedAt": "2026-10-15T17:05:00",
    "validatedBy": "Ana Control"
  }
}
```

`validatedBy` es el nombre de quien validó, o `null` si el ticket se marcó como
usado sin un controlador registrado.

### Obtener credenciales de un evento virtual

`GET /api/v1/access/virtual/{ticketId}`

Devuelve las credenciales del evento virtual para el titular autenticado del
ticket. El ticket debe estar `ACTIVE`; este acceso no está limitado por la
ventana de ingreso y registra la primera consulta en `accessedAt` de forma
idempotente.

Devuelve `200 OK` con el título, la fecha, la plataforma, el enlace y las
instrucciones de acceso.

```json
{
  "eventTitle": "Conferencia de tecnología",
  "dateTime": "2026-10-15T18:00:00",
  "platform": "Zoom",
  "accessLink": "https://zoom.example.com/event",
  "accessInstructions": "Ingresar con el nombre usado en la compra."
}
```

| Campo | Tipo | Descripción |
|------|------|-------------|
| `eventTitle` | string | Título del evento |
| `dateTime` | date-time | Fecha y hora del evento |
| `platform` | string | Plataforma de acceso |
| `accessLink` | string | Enlace HTTPS del evento |
| `accessInstructions` | string | Instrucciones del organizador |

Responde `401 Unauthorized` si no hay autenticación, `403 Forbidden` si el
usuario no es el titular, `404 Not Found` si el ticket no existe y `409
Conflict` si el ticket no está activo. Si el evento no es virtual, responde
`400 Bad Request`.

### Mis eventos como controlador

`GET /api/v1/access/my-events`

Obtiene los eventos `PUBLISHED` en los que el usuario autenticado está asignado
como controlador. La respuesta está [paginada](#paginación) y se ordena por
`dateTime` ascendente. Requiere autenticación.

---

## Tickets

Prefijo: `/api/v1/tickets`

### Estados del ticket

| Estado | Descripción |
|--------|-------------|
| `PENDING` | El pago todavía no fue confirmado |
| `ACTIVE` | El pago fue aprobado (o el evento es gratuito) y el ticket habilita el acceso |
| `USED` | El ticket presencial fue validado en la puerta |
| `CANCELLED` | El pago fue rechazado o la orden quedó sin pago |

### Órdenes de compra

Cada compra genera una **orden** con una o más entradas (`quantity`). Todas las
entradas de una orden pertenecen al mismo comprador, pero cada una tiene su propio
identificador y su propio QR, y se valida por separado en la puerta. El pago (si el
evento tiene precio) y el envío por email se hacen una sola vez por orden.

### Comprar entradas

`POST /api/v1/tickets/purchase`

Compra una o más entradas para un evento (Requiere autenticación).

- **Evento con [sectores](#sectores-sobre-un-plano):** `sectorId` es obligatorio; el
  cupo y el precio son los del sector, y el sector decide si la compra es paga o
  gratuita (un sector gratuito no pide datos de pago).
- **Evento con precio:** reserva el cupo, cobra `basePrice × quantity` en un único
  pago de MercadoPago con la cuenta del organizador y devuelve el estado resultante.
  Las entradas solo quedan `ACTIVE` (y con QR) si el pago fue aprobado.
- **Evento gratuito (`basePrice` = 0):** emite las entradas directamente en
  `ACTIVE`, sin pasar por MercadoPago. Los datos de pago se omiten.

En ambos casos, las entradas aprobadas se envían por email: para eventos
presenciales con un QR por entrada; para eventos virtuales con la plataforma, el
enlace y las instrucciones de acceso.

**Cuerpo de la petición (evento con precio):**

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "quantity": 2,
  "sectorId": "0b6f3a2e-1c4d-4e5f-9a8b-7c6d5e4f3a2b",
  "cardToken": "tok_card_1a2b3c",
  "installments": 1,
  "payerEmail": "juan@example.com",
  "paymentMethodId": "visa"
}
```

**Cuerpo de la petición (evento gratuito):**

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "quantity": 2
}
```

| Campo | Tipo | Requerido | Restricciones | Descripción |
|-------|------|-----------|---------------|-------------|
| `eventId` | uuid | Sí | — | Identificador del evento |
| `quantity` | integer | Sí | ≥ 1, máximo `ticket.purchase.max-quantity` (10 por defecto) | Cantidad de entradas |
| `sectorId` | uuid | Solo eventos con sectores | Sector del evento; prohibido si el evento no tiene sectores | Sector de las entradas |
| `cardToken` | string | Solo eventos con precio | máx. 255 caracteres | Token de la tarjeta del pagador |
| `installments` | integer | Solo eventos con precio | entre 1 y 12 | Número de cuotas |
| `payerEmail` | string | Solo eventos con precio | email válido, máx. 254 caracteres | Correo del pagador |
| `paymentMethodId` | string | Solo eventos con precio | máx. 50 caracteres, solo `[a-z0-9_]` | Identificador del método de pago |

**Respuesta exitosa: `201 Created`**

```json
{
  "orderId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "approved",
  "statusDetail": "accredited",
  "message": "Pago aprobado. Tus entradas serán enviadas por email.",
  "tickets": [
    { "ticketId": "550e8400-e29b-41d4-a716-446655440001", "qrCodeBase64": "<qr-code-base64>" },
    { "ticketId": "550e8400-e29b-41d4-a716-446655440002", "qrCodeBase64": "<qr-code-base64>" }
  ]
}
```

| Campo | Descripción |
|-------|-------------|
| `orderId` | Identificador de la orden de compra |
| `status` | Estado informado por MercadoPago: `approved`, `rejected`, `cancelled`, `pending` o `in_process`. En eventos gratuitos es siempre `approved` |
| `statusDetail` | Detalle del estado informado por MercadoPago; `free` en eventos gratuitos |
| `message` | Mensaje legible según el resultado |
| `tickets` | Entradas de la orden; `qrCodeBase64` solo se informa para eventos presenciales con la orden aprobada |

> Un pago **rechazado por la evaluación de MercadoPago** (por ejemplo, fondos
> insuficientes) se responde con `201` y `status: "rejected"`; las entradas quedan
> `CANCELLED`. El código `402` se usa cuando MercadoPago rechaza la solicitud de
> pago en sí (datos de tarjeta inválidos, por ejemplo).

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Las validaciones del cuerpo fallan, `quantity` supera el máximo por orden, faltan los datos de pago en un evento con precio, o `sectorId` falta, sobra o no pertenece al evento |
| `402 Payment Required` | MercadoPago rechazó la solicitud de pago |
| `404 Not Found` | El evento no existe |
| `409 Conflict` | No hay cupo (del evento o del sector) para la cantidad pedida, el evento no está `PUBLISHED` o pasaron más de `event.access-window.after-hours` horas desde `dateTime`, o el precio del evento cambió durante la compra |
| `428 Precondition Required` | El evento tiene precio y el organizador no tiene una cuenta de MercadoPago vinculada |
| `502 Bad Gateway` | No se pudo procesar el pago con MercadoPago o renovar el token del organizador |

Si MercadoPago no responde o devuelve un error 5xx, el resultado del cobro es
incierto: la orden queda `PENDING` y la reconciliación automática la resuelve
(aprobándola o cancelándola) en los minutos siguientes.

### Listar mis tickets

`GET /api/v1/tickets/my-tickets`

Obtiene todas las entradas del usuario autenticado, de todas sus órdenes (Requiere
autenticación). El QR se incluye solo para tickets `ACTIVE` de eventos presenciales.
Los datos de acceso de eventos virtuales se obtienen con
[`GET /api/v1/access/virtual/{ticketId}`](#obtener-credenciales-de-un-evento-virtual).

**Respuesta exitosa: `200 OK`**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "eventTitle": "Conferencia de tecnología",
    "sectorName": "VIP",
    "status": "ACTIVE",
    "boughtAt": "2026-09-01T14:30:00",
    "qrCodeBase64": "<qr-code-base64>",
    "eventType": "IN_PERSON",
    "eventDateTime": "2026-10-15T18:00:00"
  }
]
```

### Obtener ticket

`GET /api/v1/tickets/{id}`

Obtiene un ticket por su identificador (Requiere autenticación, solo el titular).

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

### Reenviar entradas por correo

`POST /api/v1/tickets/{id}/resend-email`

Vuelve a encolar el envío por email de la **orden completa** a la que pertenece el
ticket (Requiere autenticación, solo el titular). El ticket debe estar `ACTIVE`; el
email incluye solo las entradas de la orden que sigan activas.

Si la orden se envió con éxito hace menos de 5 minutos, o hay un envío en curso, la
solicitud se acepta pero no genera un nuevo envío. Si los reintentos automáticos se
habían agotado, inicia un nuevo ciclo de intentos.

**Parámetros de ruta:**

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `id` | uuid | Identificador de cualquier ticket de la orden |

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

El organizador debe vincular su cuenta de MercadoPago para publicar eventos con
precio y recibir el importe de las entradas vendidas. Los eventos gratuitos no lo
requieren.

**Flujo de vinculación (desde el frontend):**

1. El frontend llama a `GET /api/v1/mp/connect` con el token del usuario y recibe
   la URL de autorización.
2. El frontend navega a esa URL (`window.location.href`); el organizador autoriza
   en MercadoPago.
3. MercadoPago redirige el navegador a `GET /api/v1/mp/callback`; el backend vincula
   la cuenta y redirige al frontend (`mercadopago.frontend-return-url`) con el
   resultado en la query.

### Conectar cuenta

`GET /api/v1/mp/connect`

Genera un `state` de un solo uso (válido 10 minutos) y devuelve la URL de
autorización de MercadoPago (Requiere autenticación).

**Respuesta exitosa: `200 OK`**

```json
{
  "authorizationUrl": "https://auth.mercadopago.com/authorization?client_id=...&response_type=code&platform_id=mp&redirect_uri=...&state=..."
}
```

### Callback de autorización

`GET /api/v1/mp/callback`

Callback de redirección de MercadoPago. Es público porque lo abre el navegador del
organizador al volver de MercadoPago (sin el token de la API); el usuario se
identifica por el `state`.

**Parámetros de query:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `code` | string | Sí | Código de autorización devuelto por MercadoPago |
| `state` | string | Sí | Valor `state` generado en `/connect` |
| `error` | string | No | Parámetro de error de MercadoPago; si viene presente la vinculación falla |

**Respuesta: `302 Found`**

Siempre redirige a la URL configurada en `mercadopago.frontend-return-url`
(variable `MERCADO_PAGO_FRONTEND_RETURN_URL` en producción), con el resultado en la
query:

| Redirección | Significado |
|-------------|-------------|
| `?status=linked` | La cuenta quedó vinculada |
| `?status=error&reason=cancelled` | El organizador canceló la autorización, o faltan `code`/`state` |
| `?status=error&reason=invalid_state` | El `state` es inválido, ya fue usado o venció |
| `?status=error&reason=link_failed` | Falló el intercambio de tokens con MercadoPago |

Ejemplo:

```
Location: https://app.dominio.com/organizer/mercadopago?status=linked
```

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
responde `409` si el organizador tiene tickets `PENDING` o `ACTIVE` en algún
evento `PUBLISHED` **con precio**, porque esos pagos todavía pueden requerir el
token de MercadoPago para el webhook o la reconciliación. En ese caso la cuenta no
se elimina.

Si no existen esos tickets abiertos, los eventos `PUBLISHED` con precio y sin
tickets `PENDING`, `ACTIVE` ni `USED` vuelven a `DRAFT`. Los eventos que ya
vendieron o utilizaron entradas permanecen `PUBLISHED`, aunque la cuenta se
desvincule. Los eventos gratuitos no se ven afectados.

Existe una pequeña ventana de carrera entre la comprobación de tickets abiertos
y la reversión de eventos. Un ticket reservado exactamente durante ese intervalo
podría quedar pendiente sin token del organizador; es una limitación conocida de
esta operación.

**Respuesta exitosa: `204 No Content`**

**Errores:**

| Código | Causa |
|--------|-------|
| `409 Conflict` | El organizador tiene tickets `PENDING` o `ACTIVE` en un evento `PUBLISHED` con precio |
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

Cuerpo vacío. Las notificaciones que no corresponden a pagos, o de pagos ya resueltos, se responden con `200` sin hacer nada. Un pago aprobado activa todas las entradas de su orden y programa el envío por email; uno rechazado las cancela.

**Errores:**

| Código | Causa |
|--------|-------|
| `400 Bad Request` | Faltan headers o el cuerpo es inválido |
| `401 Unauthorized` | La firma es inválida, `data.id` está ausente o no coincide con el query, o el timestamp tiene más de 5 minutos de antigüedad |
| `409 Conflict` | El pago notificado todavía no está registrado localmente (MercadoPago reintentará) |
| `428 Precondition Required` | El organizador del evento ya no tiene cuenta de MercadoPago vinculada |
| `502 Bad Gateway` | No se pudo consultar el pago en MercadoPago |

---

## Esquemas de respuesta

### UserResponse

```json
{
  "id": "uuid",
  "name": "string",
  "email": "string",
  "systemRole": "string",
  "dateBirth": "date",
  "avatarUrl": "string"
}
```

| Campo | Valores |
|-------|---------|
| `systemRole` | `USER` o `ADMIN` |
| `avatarUrl` | Ruta relativa de la imagen, o `null` si no tiene avatar |

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
  "availableTickets": "integer",
  "platform": "string",
  "accessLink": "string",
  "accessInstructions": "string",
  "isOrganizer": "boolean",
  "organizerName": "string",
  "imageUrl": "string",
  "layoutImageUrl": "string",
  "sectors": [ "SectorResponse" ]
}
```

| Campo | Valores |
|-------|---------|
| `status` | `DRAFT`, `PUBLISHED`, `CANCELLED` o `FINISHED` |
| `eventType` | `IN_PERSON` o `VIRTUAL` |
| `imageUrl` | Ruta relativa de la imagen de portada (`/api/v1/images/{key}`), o `null` si el evento no tiene imagen. Ver [Descargar una imagen](#descargar-una-imagen). |
| `layoutImageUrl` | Ruta relativa del plano del recinto. Solo en el detalle (y respuestas al organizador) de eventos `IN_PERSON` con plano; se omite en otro caso. |
| `sectors` | Lista de [`SectorResponse`](#sectorresponse) ordenada por nombre. Solo en el detalle (y respuestas al organizador) de eventos `IN_PERSON`: `[]` si no tiene sectores. Se omite en listados y en eventos virtuales. |
| `organizerName` | Nombre del organizador del evento. Viene en el detalle, en los listados y en las respuestas de crear, actualizar y publicar. |
| `isOrganizer` | Si el usuario actual organiza el evento. Viene en `GET /api/v1/events/{id}` y en las respuestas de crear, actualizar y publicar (siempre `true`); en los listados se omite. |
| `availableTickets` | Entradas que quedan a la venta: `capacity` menos las entradas pendientes de pago, activas o usadas (nunca negativo). `0` significa agotado. |

Con sectores, `basePrice` es el precio mínimo entre ellos y `capacity` la suma de sus
capacidades (ver [Sectores sobre un plano](#sectores-sobre-un-plano)).

Los campos de ubicación (`address`, `latitude`, `longitude`, `capacity`) y `availableTickets` solo se llenan para eventos de tipo `IN_PERSON`; los virtuales no tienen cupo. Para eventos de tipo `VIRTUAL` se llena `platform`. `accessLink` y `accessInstructions` solo aparecen para el organizador: en `GET /api/v1/events/{id}` y en las respuestas de crear, actualizar y publicar. En los listados y para el resto de los usuarios se omiten del JSON (el enlace se entrega al comprador con `GET /api/v1/access/virtual/{ticketId}`).

### SectorResponse

```json
{
  "id": "uuid",
  "name": "string",
  "price": "number",
  "capacity": "integer",
  "color": "string",
  "points": [ { "x": "number", "y": "number" } ],
  "availableTickets": "integer"
}
```

| Campo | Valores |
|-------|---------|
| `color` | `#RRGGBB`, o `null` si no se definió |
| `points` | Vértices normalizados de 0 a 1 respecto del plano, o `null` si el sector no se dibujó |
| `availableTickets` | `capacity` menos las entradas del sector pendientes de pago, activas o usadas |

### PurchaseResponse

```json
{
  "orderId": "uuid",
  "status": "string",
  "statusDetail": "string",
  "message": "string",
  "tickets": [
    { "ticketId": "uuid", "qrCodeBase64": "string" }
  ]
}
```

| Campo | Valores |
|-------|---------|
| `status` | Estado de MercadoPago (`approved`, `rejected`, `cancelled`, `pending`, `in_process`); `approved` en eventos gratuitos |
| `statusDetail` | Detalle de MercadoPago; `free` en eventos gratuitos |
| `tickets[].qrCodeBase64` | `null` si la orden no fue aprobada o el evento es virtual |

### TicketResponse

```json
{
  "id": "uuid",
  "eventId": "uuid",
  "eventTitle": "string",
  "sectorName": "string",
  "status": "string",
  "boughtAt": "datetime",
  "qrCodeBase64": "string",
  "eventType": "string",
  "eventDateTime": "datetime"
}
```

| Campo | Valores |
|-------|---------|
| `status` | `PENDING`, `ACTIVE`, `USED` o `CANCELLED` |
| `eventType` | `IN_PERSON` o `VIRTUAL` |
| `eventDateTime` | Fecha y hora de inicio del evento |
| `sectorName` | Sector de la entrada, o `null` si el evento no tiene sectores |
| `qrCodeBase64` | `null` si el ticket no está `ACTIVE` o el evento es virtual |

### ControllerResponse

```json
{
  "userId": "uuid",
  "name": "string",
  "email": "string"
}
```

### AccessValidationResponse

```json
{
  "ticketId": "uuid",
  "attendeeName": "string",
  "eventTitle": "string",
  "sectorName": "string",
  "validatedAt": "datetime"
}
```

### VirtualAccessResponse

```json
{
  "eventTitle": "string",
  "dateTime": "datetime",
  "platform": "string",
  "accessLink": "string",
  "accessInstructions": "string"
}
```

### MpAuthorizationUrlResponse

```json
{
  "authorizationUrl": "string"
}
```

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
  "fields": {},
  "details": {}
}
```

`fields` solo aparece en errores de validación y mapea cada campo con su mensaje de error.
`details` solo aparece en errores de negocio que aportan datos extra (hoy, el `409` de
ticket ya utilizado).
