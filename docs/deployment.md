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
| `spring.sql.init.mode` | `always` (carga `db/dev-seed.sql`) | `always` (ejecuta `db/schema.sql`; no carga datos) |
| `spring.jpa.show-sql` | `true` | `false` |
| Swagger (`springdoc.*.enabled`) | `true` | `false` |
| `cors.allowed-origins` | `http://localhost:4200` | `${CORS_ALLOWED_ORIGINS}` |
| `mercadopago.redirect-uri` | `http://localhost:8085/api/v1/mp/callback` | `${MERCADO_PAGO_REDIRECT_URI}` |
| `mercadopago.frontend-return-url` | `http://localhost:4200/organizer/mercadopago` | `${MERCADO_PAGO_FRONTEND_RETURN_URL}` |

En `prod` Hibernate **no** crea el esquema: lo prepara `db/schema.sql` al arrancar (ver
[Base de datos](#base-de-datos)) y después Hibernate comprueba que coincida con las entidades.
Nunca se cargan datos de prueba.

## Variables de entorno

Todas las variables de [`setup.md`](setup.md#variables-de-entorno) son necesarias.
En producción, además:

| Variable | Valor en producción |
|----------|---------------------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | URL JDBC de la base, si no se llama `db` (por ejemplo, una base administrada). |
| `CORS_ALLOWED_ORIGINS` | Origen(es) reales del frontend con esquema, separados por coma (`https://app.dominio.com`). Con cookies de sesión deben ser exactos. |
| `COOKIE_SECURE` | `true` (el valor por defecto): las cookies de sesión solo viajan por HTTPS. |
| `COOKIE_SAME_SITE` | `Strict` si el frontend y la API comparten sitio (mismo dominio o subdominios, por ejemplo `app.dominio.com` y `api.dominio.com`); `None` solo si están en dominios distintos. |
| `JWT_SECRET_KEY` | Clave Base64 de al menos 32 bytes, única para producción (`openssl rand -base64 32`). |
| `MERCADO_PAGO_REDIRECT_URI` | URL pública HTTPS de `/api/v1/mp/callback`. |
| `MERCADO_PAGO_FRONTEND_RETURN_URL` | Ruta del frontend a la que vuelve el organizador tras vincular MercadoPago. |
| `IMAGES_STORAGE_PATH` | Directorio de las imágenes dentro del contenedor (`/app/uploads`, ya definido en `docker-compose.yaml`). |
| `POSTGRES_*`, `SMTP_*`, `MERCADO_PAGO_*` | Credenciales reales, idealmente gestionadas por un gestor de secretos del proveedor. |

> Nunca uses los valores de `.env.template` en producción ni subas el `.env` real al
> repositorio.

## Base de datos

- **Motor:** PostgreSQL 15 o superior.
- **Esquema:** en `prod` Hibernate corre con `validate`: comprueba el esquema pero no lo
  crea. Lo prepara el script `src/main/resources/db/schema.sql`, que se ejecuta en cada
  arranque, **antes** de la validación, y es idempotente:
  - **Parte 1:** `CREATE TABLE IF NOT EXISTS` de todas las tablas. Con un volumen vacío
    crea el esquema completo; en una base existente solo agrega las tablas que falten
    (por ejemplo `audit_logs`).
  - **Parte 2:** `ADD COLUMN IF NOT EXISTS` de las columnas agregadas después de la primera
    versión (bloqueo y datos de cuenta en `users`, `image_key`, `layout_image_key`,
    `sector_id`) y la corrección de las coordenadas a `numeric(10,7)`. Completa bases
    creadas con una versión anterior sin perder datos.

  No hay pasos manuales ni dependencias de migración. Tampoco replica las restricciones
  `CHECK` de los enums: Java ya valida el enum, y un valor nuevo no debe obligar a
  modificar la base.

  **Al cambiar una entidad** (tabla o columna nueva), agregala a `db/schema.sql`: la tabla
  en la parte 1 y, si la tabla ya existía en producción, la columna también en la parte 2.
  `SchemaScriptTest` arranca PostgreSQL vacío, ejecuta el script y deja que Hibernate valide:
  si falta algo, falla. `SchemaScriptUpgradeTest` comprueba que el script se puede repetir
  y que completa un esquema viejo conservando los datos.

  Los valores de coordenadas guardados cuando las columnas redondeaban a grados enteros
  quedaron redondeados y hay que volver a cargarlos.

  **Límites.** No es una herramienta de migraciones: no guarda historial y no resuelve
  renombres de columnas, cambios de tipo ni rellenos de datos, que necesitan SQL específico.
  El usuario de la base que usa la aplicación necesita permiso para `CREATE` y `ALTER`.
  El test no detecta diferencias que Hibernate no valida (restricciones, índices).
- **Imágenes:** las portadas, los planos y los avatares se guardan en el volumen `uploads`
  (ver [Imágenes de eventos](#imágenes-de-eventos)).
  **Primer superusuario.** En producción no hay seed. Definí `INITIAL_ADMIN_EMAIL` e
  `INITIAL_ADMIN_PASSWORD` en el primer arranque: se crea el superusuario solo si no
  existe ninguno (y nunca promueve a un usuario existente). Después quitá la contraseña
  del entorno y cambiala desde la cuenta (`/auth/me/password`).
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
- **Esquema.** La columna `events.image_key` la agrega `db/schema.sql` si falta.

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
- [ ] El backend arranca con `prod`: `db/schema.sql` prepara el esquema y Hibernate lo valida.
- [ ] Base de datos sin acceso público y con backups.
- [ ] `CORS_ALLOWED_ORIGINS` con el dominio real del frontend.
- [ ] `COOKIE_SECURE=true` y `COOKIE_SAME_SITE` acorde a dónde se aloja el frontend.
- [ ] `MERCADO_PAGO_REDIRECT_URI`, URL de webhooks y `MERCADO_PAGO_FRONTEND_RETURN_URL` por HTTPS.
- [ ] Reloj sincronizado (NTP) para la validación de firma de webhooks.
- [ ] TLS terminado en el proxy inverso o balanceador.
- [ ] Nginx con `deploy/nginx/`: `client_max_body_size 6m` y `/api/v1/images/` servido desde el volumen.
- [ ] Primer superusuario creado y `INITIAL_ADMIN_PASSWORD` retirada del entorno.
- [ ] Respaldo periódico del volumen `uploads`, además del de la base.
- [ ] Una sola instancia del backend (o coordinación de tareas programadas).

## Guías por proveedor

### Oracle Cloud

_Pendiente._ Instrucciones para desplegar el backend en Oracle Cloud
Infrastructure (por ejemplo, una VM Compute con Docker Compose o un servicio de
contenedores, con base de datos PostgreSQL).
