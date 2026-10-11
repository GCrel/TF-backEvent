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

Levanta PostgreSQL y el backend en una red común:

```bash
cp .env.template .env     # completar valores
docker compose up --build
```

- API: `http://localhost:8085`
- Swagger UI: `http://localhost:8085/swagger-ui.html` (si `SPRING_PROFILES_ACTIVE=dev`)
- Los datos de PostgreSQL persisten en el volumen `pgdata` (con perfil `dev` el
  esquema igualmente se recrea en cada arranque).
- PostgreSQL queda expuesto en `localhost:5432` para poder conectarse desde
  herramientas locales.

Para detener:

```bash
docker compose down          # conserva datos
docker compose down -v       # elimina el volumen de PostgreSQL
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

## Datos de prueba (perfil dev)

Con `SPRING_PROFILES_ACTIVE=dev`:

- Hibernate recrea el esquema en cada arranque (`ddl-auto=create`).
- Se carga `src/main/resources/data.sql` con usuarios, eventos, órdenes, tickets,
  pagos y participaciones de ejemplo.
- La contraseña de **todos** los usuarios sembrados es `password`.

Usuarios destacados:

| Email | Rol de sistema | Notas |
|-------|----------------|-------|
| `ana.garcia@example.com` | `ADMIN` | Organizadora y con cuenta de MercadoPago vinculada. |
| `bruno.lopez@example.com` | `USER` | Organizador y asistente. |
| `carla.mendez@example.com` | `USER` | Asistente y controladora. |
| `diego.torres@example.com` | `USER` | Cuenta **no verificada**. Su código sembrado (`ABC123`) no cumple el formato de 6 dígitos: para verificarla, pedí un código nuevo con `/auth/resend-verification`. |

> `data.sql` está pendiente de revisión: las fechas de los eventos y los tokens
> sembrados pueden no reflejar escenarios vigentes.

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
