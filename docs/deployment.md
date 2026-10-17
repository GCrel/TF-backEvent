# Despliegue — TF-backEvent

Guía de despliegue del backend en un entorno de producción. Para levantarlo en
local ver [`setup.md`](setup.md).

Las primeras secciones describen los **requisitos comunes** a cualquier proveedor.
Las instrucciones específicas de cada plataforma se agregan en
[Guías por proveedor](#guías-por-proveedor).

## Índice

- [Imagen Docker](#imagen-docker)
- [Perfil de producción](#perfil-de-producción)
- [Variables de entorno](#variables-de-entorno)
- [Base de datos](#base-de-datos)
- [MercadoPago](#mercadopago)
- [HTTPS y proxy inverso](#https-y-proxy-inverso)
- [Imágenes de eventos](#imágenes-de-eventos)
- [Operación y escalado](#operación-y-escalado)
- [Checklist de despliegue](#checklist-de-despliegue)
- [Guías por proveedor](#guías-por-proveedor)

## Imagen Docker

El `Dockerfile` es **multietapa**:

1. **Build** — imagen `maven:3.9.11-eclipse-temurin-21`: descarga dependencias y
   empaqueta el JAR (`mvn clean package -DskipTests`).
2. **Runtime** — imagen `eclipse-temurin:21-jre-alpine`: copia el JAR y lo ejecuta
   en el puerto `8085`.

```bash
docker build -t tf-backevent:latest .
docker run --rm -p 8085:8085 --env-file .env tf-backevent:latest
```

La imagen no incluye la base de datos: necesita un PostgreSQL accesible (ver
[Base de datos](#base-de-datos)).

## Perfil de producción

Producción usa el perfil **`prod`** (`SPRING_PROFILES_ACTIVE=prod`). Diferencias con
`dev`:

| Propiedad | `dev` | `prod` |
|-----------|-------|--------|
| `spring.jpa.hibernate.ddl-auto` | `create` (recrea el esquema) | `validate` |
| `spring.sql.init.mode` | `always` (carga `data.sql`) | `never` |
| `spring.jpa.show-sql` | `true` | `false` |
| Swagger (`springdoc.*.enabled`) | `true` | `false` |
| `cors.allowed-origins` | `http://localhost:4200` | `${CORS_ALLOWED_ORIGINS}` |
| `mercadopago.redirect-uri` | `http://localhost:8085/api/v1/mp/callback` | `${MERCADO_PAGO_REDIRECT_URI}` |
| `mercadopago.frontend-return-url` | `http://localhost:4200/organizer/mercadopago` | `${MERCADO_PAGO_FRONTEND_RETURN_URL}` |

En `prod` el esquema **no** se crea ni se siembra: debe existir y coincidir con las
entidades.

## Variables de entorno

Todas las variables de [`setup.md`](setup.md#variables-de-entorno) son necesarias.
En producción, además:

| Variable | Valor en producción |
|----------|---------------------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | URL JDBC de la base, si no se llama `db` (por ejemplo, una base administrada). |
| `CORS_ALLOWED_ORIGINS` | Origen(es) reales del frontend con esquema, separados por coma (`https://app.dominio.com`). |
| `JWT_SECRET_KEY` | Clave Base64 de al menos 32 bytes, única para producción (`openssl rand -base64 32`). |
| `MERCADO_PAGO_REDIRECT_URI` | URL pública HTTPS de `/api/v1/mp/callback`. |
| `MERCADO_PAGO_FRONTEND_RETURN_URL` | Ruta del frontend a la que vuelve el organizador tras vincular MercadoPago. |
| `IMAGES_STORAGE_PATH` | Directorio de las imágenes dentro del contenedor (`/app/uploads`, ya definido en `docker-compose.yaml`). |
| `POSTGRES_*`, `SMTP_*`, `MERCADO_PAGO_*` | Credenciales reales, idealmente gestionadas por un gestor de secretos del proveedor. |

> Nunca uses los valores de `.env.template` en producción ni subas el `.env` real al
> repositorio.

## Base de datos

- **Motor:** PostgreSQL 15 o superior.
- **Esquema:** con `validate`, Hibernate comprueba el esquema pero no lo crea. Antes
  del primer despliegue hay que provisionarlo. Tablas esperadas:

  ```text
  users, refresh_token, events, in_person_events, virtual_events,
  ticket_orders, tickets, payments, event_participations,
  organizer_mp_accounts, mp_oauth_states
  ```

  El proyecto no incluye migraciones versionadas (Flyway/Liquibase). Una opción
  práctica es levantar una vez con perfil `dev` contra una base vacía para que
  Hibernate genere el esquema, exportarlo (`pg_dump --schema-only`) y aplicarlo en
  producción. Cada cambio del modelo requiere repetir este proceso.
- **Red:** la base no debe ser accesible desde Internet; solo desde el backend.
- **Respaldo:** configurar backups periódicos (los de la base administrada del
  proveedor o `pg_dump` programado).

## MercadoPago

1. Crear/configurar la aplicación en el panel de desarrolladores de MercadoPago.
2. Registrar como URL de redirección OAuth la misma que `MERCADO_PAGO_REDIRECT_URI`
   (`https://<dominio-api>/api/v1/mp/callback`).
3. Configurar la **URL de webhooks** de pagos:
   `https://<dominio-api>/api/v1/webhooks/payments`.
4. Cargar `MERCADO_PAGO_CLIENT_ID`, `MERCADO_PAGO_CLIENT_SECRET` y
   `MERCADO_PAGO_WEBHOOK_SECRET` con los valores de producción.

Los webhooks se validan con firma HMAC-SHA256 y toleran un desfase máximo de 5
minutos, por lo que el reloj del servidor debe estar sincronizado (NTP).

## HTTPS y proxy inverso

La API debe exponerse siempre por **HTTPS** (MercadoPago lo exige para el callback y
los webhooks). El proyecto incluye Nginx como proxy inverso (ver
[Imágenes de eventos](#imágenes-de-eventos)); ese Nginx, o el balanceador que se
ponga delante, debe:

- Termine TLS.
- Redirija el tráfico al backend en el puerto `8085`.
- Reenvíe los headers `Authorization` y los de MercadoPago (`x-signature`,
  `x-request-id`).

## Imágenes de eventos

Las imágenes se guardan en disco y las sirve Nginx (ver
[ADR-016](decisions.md#adr-016-imágenes-en-volumen-local-servidas-por-nginx)). La
configuración está en `deploy/nginx/`:

- `default.conf`: servidor que reenvía la API al backend (`backend:8085`) y fija
  `client_max_body_size 6m`. Con el valor por defecto de Nginx (1 MB) las subidas
  fallan con `413` antes de llegar al backend.
- `images.conf`: atiende `GET /api/v1/images/<uuid>.(webp|jpg|png)` desde el
  volumen, con `X-Content-Type-Options: nosniff` y caché inmutable. Cualquier otra
  ruta bajo `/api/v1/images/` responde `404`.

Requisitos:

- **Volumen compartido.** El backend escribe en el volumen `uploads`
  (`/app/uploads`) y Nginx lo monta en **solo lectura** (`/srv/images`). Ambos
  deben correr en el mismo servidor.
- **TLS.** Si Nginx también termina TLS, agregá el bloque `listen 443 ssl` con los
  certificados al `default.conf` (o poné delante el proxy que termine TLS y que
  reenvíe a este Nginx). Si `/api/v1/images/` se enruta directo al backend, las
  imágenes no se sirven.
- **Respaldo.** El volumen `uploads` no está en la base: respaldalo aparte, por
  ejemplo:

  ```bash
  docker run --rm -v tf-backevent_uploads:/data:ro -v "$PWD":/backup alpine \
    tar czf /backup/uploads-$(date +%F).tar.gz -C /data .
  ```

  El nombre real del volumen lleva el prefijo del proyecto de Compose (verificalo
  con `docker volume ls`).
- **Esquema.** La columna `events.image_key` es nueva. Sobre una base existente:

  ```sql
  ALTER TABLE events ADD COLUMN image_key varchar(255);
  ```

## Operación y escalado

- **Una sola instancia.** La API es stateless, pero `EventService`,
  `PaymentRecoveryService` y `DeliveryService` usan `@Scheduled`: con varias
  instancias cada una ejecutaría las tareas. Los bloqueos optimistas evitan efectos
  dobles en pagos y estados, pero para escalar horizontalmente conviene agregar
  coordinación (por ejemplo, ShedLock) antes.
- **Logs:** la aplicación escribe a `stdout`; recolectalos con el servicio de logs
  del proveedor.
- **Reinicio automático:** configurar la política de reinicio del contenedor o
  servicio (`restart: always` en Compose, o la equivalente del proveedor).

## Checklist de despliegue

- [ ] Variables de entorno de producción completas, con secretos reales y fuertes.
- [ ] `SPRING_PROFILES_ACTIVE=prod`.
- [ ] Esquema de base de datos provisionado y compatible (`validate`).
- [ ] Base de datos sin acceso público y con backups.
- [ ] `CORS_ALLOWED_ORIGINS` con el dominio real del frontend.
- [ ] `MERCADO_PAGO_REDIRECT_URI`, URL de webhooks y `MERCADO_PAGO_FRONTEND_RETURN_URL` por HTTPS.
- [ ] Reloj sincronizado (NTP) para la validación de firma de webhooks.
- [ ] TLS terminado en el proxy inverso o balanceador.
- [ ] Nginx con `deploy/nginx/`: `client_max_body_size 6m` y `/api/v1/images/` servido desde el volumen.
- [ ] Columna `events.image_key` presente en el esquema.
- [ ] Respaldo periódico del volumen `uploads`, además del de la base.
- [ ] Una sola instancia del backend (o coordinación de tareas programadas).

## Guías por proveedor

### Oracle Cloud

_Pendiente._ Instrucciones para desplegar el backend en Oracle Cloud
Infrastructure (por ejemplo, una VM Compute con Docker Compose o un servicio de
contenedores, con base de datos PostgreSQL).
