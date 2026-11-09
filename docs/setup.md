# Instalación y ejecución — TF-backEvent

Guía para levantar el backend en un entorno local. Para desplegar en producción
ver [`deployment.md`](deployment.md).

## Índice

- [Requisitos previos](#requisitos-previos)
- [Variables de entorno](#variables-de-entorno)
- [Opción A: Docker Compose (recomendada)](#opción-a-docker-compose-recomendada)
- [Opción B: Maven local](#opción-b-maven-local)
- [Datos de prueba (perfil dev)](#datos-de-prueba-perfil-dev)
- [Swagger UI](#swagger-ui)
- [Pruebas](#pruebas)
- [Compilar el JAR](#compilar-el-jar)
- [Referencia de configuración](#referencia-de-configuración)

## Requisitos previos

| Herramienta | Versión | Notas |
|-------------|---------|-------|
| Java (JDK) | 21 | Requerido por el `pom.xml`. |
| Maven | 3.9+ | O usar el wrapper incluido `./mvnw`. |
| Docker o Podman | Docker 24+ | Para PostgreSQL, Compose y las pruebas con Testcontainers. |
| Docker Compose | v2 | Para la opción A. |

Servicios externos (para funcionalidad completa):

- **Servidor SMTP** — envío de códigos de verificación y entradas.
- **Cuenta de MercadoPago + aplicación OAuth** — para publicar eventos pagos y
  cobrar. Los eventos gratuitos funcionan sin MercadoPago.

## Variables de entorno

El proyecto lee su configuración de variables de entorno. La plantilla está en
`.env.template`; copiala a `.env` y completá los valores:

```bash
cp .env.template .env
```

> `.env` está en `.gitignore`. Nunca subas credenciales reales al repositorio.

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `POSTGRES_DB` | Nombre de la base de datos. | `tf_event_db` |
| `POSTGRES_USER` | Usuario de PostgreSQL. | `user` |
| `POSTGRES_PASSWORD` | Contraseña de PostgreSQL. | `pass` |
| `JWT_SECRET_KEY` | Clave HMAC **en Base64**, de al menos 32 bytes decodificada. | salida de `openssl rand -base64 32` |
| `JWT_ACCESS_TOKEN_EXPIRATION` | Vida del access token en **ms**. | `1800000` (30 min) |
| `JWT_REFRESH_TOKEN_EXPIRATION` | Vida del refresh token en **ms**. | `604800000` (7 días) |
| `SMTP_HOST` | Host del servidor SMTP. | `smtp.gmail.com` |
| `SMTP_PORT` | Puerto SMTP. | `587` |
| `SMTP_USERNAME` | Usuario SMTP. | `api@gmail.com` |
| `SMTP_PASSWORD` | Contraseña / app password SMTP. | — |
| `SMTP_FROM` | Remitente de los correos. | `no-reply@dominio.com` |
| `MERCADO_PAGO_ACCESS_TOKEN` | Access token de la aplicación. **No se usa actualmente**: los pagos usan el token OAuth de cada organizador. | — |
| `MERCADO_PAGO_WEBHOOK_SECRET` | Secreto para validar la firma de los webhooks. | — |
| `MERCADO_PAGO_CLIENT_ID` | Client ID de la app OAuth. | — |
| `MERCADO_PAGO_CLIENT_SECRET` | Client Secret de la app OAuth. | — |
| `MERCADO_PAGO_REDIRECT_URI` | URI de callback OAuth (solo `prod`; en `dev` es `http://localhost:8085/api/v1/mp/callback`). | `https://dominio/api/v1/mp/callback` |
| `MERCADO_PAGO_FRONTEND_RETURN_URL` | Ruta del frontend a la que vuelve el organizador tras vincular MercadoPago (solo `prod`; en `dev` es `http://localhost:4200/organizer/mercadopago`). | `https://app.dominio.com/organizer/mercadopago` |
| `INITIAL_ADMIN_EMAIL` | Email del primer superusuario. Se crea al arrancar **solo si no existe ningún `ADMIN`** (en `dev` ya hay uno en `data.sql`). Opcional. | `admin@dominio.com` |
| `INITIAL_ADMIN_PASSWORD` | Contraseña del primer superusuario (8 a 72 caracteres). Quitala del entorno después del primer arranque. | — |
| `INITIAL_ADMIN_NAME` | Nombre del primer superusuario. | `Administrador` |
| `COOKIE_SECURE` | Marca `Secure` en las cookies de sesión. Por defecto `true`: en desarrollo sobre `http` poné `false`. | `false` (dev), `true` (prod) |
| `COOKIE_SAME_SITE` | `SameSite` de las cookies de sesión: `Strict` (por defecto) o `None` si frontend y API están en dominios distintos (exige `COOKIE_SECURE=true`). | `Strict` |
| `SPRING_PROFILES_ACTIVE` | Perfil activo: `dev` o `prod`. | `dev` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos, con esquema y separados por coma (solo `prod`; en `dev` se permite `http://localhost:4200`). | `https://app.dominio.com` |

> **Clave JWT.** `JWT_SECRET_KEY` debe ser una cadena **Base64** que, decodificada,
> tenga al menos **32 bytes (256 bits)**; con menos, la firma de los JWT falla.
> `openssl rand -base64 32` genera una válida (44 caracteres). La plantilla trae un
> valor de relleno que hay que reemplazar.
>
> **Perfil.** La plantilla usa `dev`. Con `prod` la aplicación exige un esquema ya
> creado y no arranca sobre una base vacía.

> **CORS.** Los orígenes deben incluir el esquema (`http://localhost:3000`, no
> `localhost:3000`). Si el frontend local corre en otro puerto que `4200`, ajustá
> `cors.allowed-origins` en `application-dev.properties`.

## Opción A: Docker Compose (recomendada)

Levanta PostgreSQL, el backend y Nginx en una red común. Nginx es la entrada de la
API (reenvía al backend) y sirve las imágenes de los eventos, igual que en
producción:

```bash
cp .env.template .env     # completar valores
docker compose up --build
```

- API (vía Nginx): `http://localhost:8080`. Es la URL base que debe usar el
  frontend: las imágenes (`/api/v1/images/...`) solo se sirven por acá.
- Backend directo: `http://localhost:8085` (el callback OAuth de MercadoPago en
  `dev` apunta a este puerto).
- Swagger UI: `http://localhost:8085/swagger-ui.html` (si `SPRING_PROFILES_ACTIVE=dev`)
- Los datos de PostgreSQL persisten en el volumen `pgdata` (con perfil `dev` el
  esquema igualmente se recrea en cada arranque).
- Las imágenes subidas persisten en el volumen `uploads`. Como el esquema se
  recrea, después de reiniciar quedan archivos sin evento asociado; se borran con
  `docker compose down -v`.
- La configuración de Nginx está en `deploy/nginx/` (`default.conf` y
  `images.conf`).
- PostgreSQL queda expuesto en `localhost:5432` para poder conectarse desde
  herramientas locales.

Para detener:

```bash
docker compose down          # conserva datos
docker compose down -v       # elimina los volúmenes de PostgreSQL e imágenes
```

El servicio `backend` espera a que la base de datos pase su `healthcheck` antes de
arrancar.

## Opción B: Maven local

Útil para desarrollo con recarga automática (devtools) y depuración.

1. Levantá solo PostgreSQL con Compose:

   ```bash
   docker compose up db
   ```

2. Exportá las variables de entorno (podés cargar el `.env`):

   ```bash
   set -a && source .env && set +a
   export SPRING_PROFILES_ACTIVE=dev
   export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/${POSTGRES_DB}"
   ```

   > El `application.properties` apunta al host `db` (nombre del servicio en
   > Compose). Ejecutando fuera de Docker hay que sobrescribir
   > `SPRING_DATASOURCE_URL` a `localhost`.

3. Ejecutá la aplicación:

   ```bash
   ./mvnw spring-boot:run
   ```

> Sin Nginx, las imágenes subidas se guardan en `./uploads` (configurable con
> `IMAGES_STORAGE_PATH`) pero nadie las sirve: para verlas en el frontend usá la
> opción A.

## Datos de prueba (perfil dev)

Con `SPRING_PROFILES_ACTIVE=dev`:

- Hibernate recrea el esquema en cada arranque (`ddl-auto=create`).
- Se carga `src/main/resources/data.sql` con usuarios, eventos, órdenes, tickets,
  pagos y participaciones de ejemplo.
- Las fechas son **relativas al momento del arranque**, así que los escenarios
  (evento próximo, evento en curso, evento finalizado) siempre están vigentes.
- La contraseña de **todos** los usuarios sembrados es `password`.

Usuarios:

| Email | Rol de sistema | Para probar |
|-------|----------------|-------------|
| `ana.garcia@example.com` | `ADMIN` | Organizadora de TechConf (presencial, pago, en 30 días), del Webinar (virtual, pago, en 7 días) y del Hackathon (finalizado). Tiene MercadoPago vinculado con tokens ficticios. Tiene 2 entradas gratuitas activas del Meetup. |
| `bruno.lopez@example.com` | `USER` | Organizador del Meetup (gratuito, **en curso**: la ventana de ingreso está abierta) y del Workshop (borrador pago: publicarlo devuelve `428` porque no tiene MercadoPago). Tiene 2 entradas activas de TechConf. |
| `carla.mendez@example.com` | `USER` | Controladora de TechConf y del Meetup: puede validar ahora los QR activos del Meetup. Tiene una entrada del Webinar (acceso virtual), una entrada usada del Meetup y una compra rechazada de TechConf. |
| `diego.torres@example.com` | `USER` | Cuenta **sin verificar**; código `482913`, válido por 1 día desde el arranque. |

Identificadores útiles:

| Recurso | Id |
|---------|----|
| Meetup (en curso, gratuito) | `aaaaaaaa-0000-0000-0000-000000000002` |
| Entradas activas del Meetup (QR) | `cccccccc-0000-0000-0000-000000000005`, `cccccccc-0000-0000-0000-000000000006` |
| Entrada del Webinar de Carla | `cccccccc-0000-0000-0000-000000000004` |
| Refresh token vigente de Ana / Bruno | `5f0c6a1e-2b7d-4c3a-9e8f-1a2b3c4d5e01` / `...5e02` |
| Refresh token vencido de Carla | `5f0c6a1e-2b7d-4c3a-9e8f-1a2b3c4d5e03` |

> Los tokens de MercadoPago sembrados son ficticios: comprar entradas de eventos
> pagos sembrados falla al contactar a MercadoPago. Para probar cobros reales,
> vinculá una cuenta de prueba con `/api/v1/mp/connect`. Los eventos gratuitos se
> pueden comprar sin MercadoPago.

> En el perfil `prod` Hibernate usa `ddl-auto=validate` y **no** se cargan datos de
> prueba (`spring.sql.init.mode=never`). El esquema de producción se gestiona
> externamente.

## Swagger UI

Disponible únicamente con el perfil `dev`:

- UI: `http://localhost:8085/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8085/v3/api-docs`

En `prod` está deshabilitado (`springdoc.swagger-ui.enabled=false`).

## Pruebas

```bash
./mvnw test
```

Las pruebas unitarias y de controladores usan mocks. Las pruebas de repositorio
(`TicketAccessConcurrencyTest`, `TicketOrderQueriesTest`) usan **Testcontainers**
con PostgreSQL real; si no hay un motor de contenedores disponible, **se omiten**
(aparecen como `Skipped`).

Con **Podman** en lugar de Docker, habilitá el socket de usuario e indicáselo a
Testcontainers:

```bash
systemctl --user start podman.socket
DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock \
TESTCONTAINERS_RYUK_DISABLED=true ./mvnw test
```

## Compilar el JAR

```bash
./mvnw clean package -DskipTests
java -jar target/TF-backEvent-0.0.1-SNAPSHOT.jar
```

El `Dockerfile` hace exactamente esto en una primera etapa multietapa y luego copia
el JAR a una imagen `eclipse-temurin:21-jre-alpine`.

## Referencia de configuración

Valores definidos por defecto en `application.properties`:

| Propiedad | Valor | Descripción |
|-----------|-------|-------------|
| `server.port` | `8085` | Puerto HTTP de la API. |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Validación del esquema (se recrea en `dev`). |
| `payment.recovery.retry-delay-ms` | `60000` | Intervalo de reconciliación de pagos. |
| `payment.recovery.pending-after-minutes` | `5` | Antigüedad mínima de un pago para reconciliar. |
| `payment.recovery.orphan-after-minutes` | `15` | Antigüedad para resolver órdenes sin pago registrado. |
| `ticket.purchase.max-quantity` | `10` | Máximo de entradas por orden. |
| `ticket.delivery.retry-delay-ms` | `5000` | Intervalo con que se revisan las entregas pendientes (valor por defecto del código). |
| `ticket.delivery.max-attempts` | `8` | Intentos de envío por email antes de dejar la orden en `FAILED`. |
| `ticket.delivery.initial-backoff-minutes` | `1` | Espera tras el primer fallo de envío (se duplica en cada intento). |
| `ticket.delivery.max-backoff-minutes` | `60` | Espera máxima entre intentos de envío. |
| `event.access-window.before-hours` | `3` | Horas de ingreso antes del evento. |
| `event.access-window.after-hours` | `12` | Horas de ingreso/compra después del evento. |
| `event.finish.retry-delay-ms` | `60000` | Intervalo del cierre automático de eventos. |
| `storage.images.path` | `${IMAGES_STORAGE_PATH:uploads}` | Directorio de las imágenes de eventos (`/app/uploads` en Compose). |
| `spring.servlet.multipart.max-file-size` | `5MB` | Tamaño máximo de una imagen. |
| `spring.servlet.multipart.max-request-size` | `6MB` | Tamaño máximo del request multipart. |
