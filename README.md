# SGE — Backend (TF-backEvent)

API REST del **Sistema de Gestión de Eventos (SGE)**, una plataforma web para la
venta de boletos y el control de acceso a eventos presenciales y virtuales.

> Trabajo Final de Grado — Universidad Nacional de Chilecito (UNdeC)
> Licenciatura en Sistemas — Escuela de Ingeniería
> **Alumno:** Coronel, Gabriel Omar

Este repositorio contiene **únicamente el backend**. El frontend (Angular) vive en
el repositorio `TF-frontEvent` y consume esta API vía HTTPS.

---

## ¿Qué es?

El backend expone una API REST que cubre el ciclo completo de un evento:

- Registro y autenticación de usuarios con verificación de email.
- Creación y administración de eventos presenciales y virtuales.
- Compra de una o varias entradas por orden, con pago online a través de
  MercadoPago o sin pago para eventos gratuitos.
- Entrega de entradas digitales por email (un QR por entrada en eventos
  presenciales, datos de acceso en eventos virtuales).
- Control de acceso presencial mediante escaneo de QR.
- Acceso virtual para los titulares de una entrada activa.

## ¿Por qué?

El objetivo es centralizar la operación de eventos y resolver tres problemas
concretos en una sola plataforma:

1. **Gestión**: los organizadores publican y administran eventos sin herramientas
   dispersas.
2. **Cobro**: el dinero se acredita directamente en la cuenta de MercadoPago del
   organizador (la plataforma no intermedia los fondos).
3. **Acceso**: se evita el doble ingreso con entradas digitales individuales y
   validación atómica en la puerta.

## Alcance

| Funcionalidad | Estado |
|---------------|--------|
| Registro y autenticación con verificación de email | Implementado |
| Creación y administración de eventos (presencial / virtual) | Implementado|
| Compra de entradas con pago online (MercadoPago) | Implementado |
| Varias entradas por orden y eventos gratuitos | Implementado |
| Entradas digitales con código QR | Implementado |
| Control de acceso presencial por escaneo de QR | Implementado |
| Acceso virtual para titulares de entradas | Implementado |
| Administración global (rol `ADMIN`) | Pendiente |
| Módulo de reportes y estadísticas para organizadores | Pendiente |

### Fuera del alcance

- Aplicación móvil nativa.
- Integración con redes sociales.
- Streaming en vivo dentro de la plataforma.

## Roles

El sistema maneja **dos niveles** de roles:

**Rol de sistema** (`SystemRole`), atributo fijo del usuario:

| Rol | Descripción |
|-----|-------------|
| `USER` | Usuario registrado. Puede crear eventos y comprar entradas. |
| `ADMIN` | Reservado para la gestión global de usuarios y eventos (aún sin endpoints). |

**Rol por evento** (`EventRole`), definido en la participación del usuario en un
evento concreto (no es un atributo fijo):

| Rol | Descripción |
|-----|-------------|
| `ORGANIZER` | Creador del evento. Puede publicar, editar, cancelar y asignar controladores. |
| `CONTROLLER` | Personal habilitado para validar ingresos con QR. |

Los compradores no necesitan un rol por evento: sus entradas se asocian a ellos a
través de sus órdenes de compra. (`EventRole.ATTENDEE` existe en el modelo pero hoy
no se asigna.)

## Stack tecnológico

- **Lenguaje / Runtime:** Java 21
- **Framework:** Spring Boot 4.1.0 (Web MVC, Data JPA, Security, Validation, Mail)
- **Base de datos:** PostgreSQL 15
- **Autenticación:** JWT (access + refresh tokens) con Spring Security
- **Pagos:** MercadoPago SDK Java 3.6.0 (OAuth por organizador + webhooks)
- **QR:** ZXing 3.5.3
- **Documentación de API:** springdoc-openapi (Swagger UI, solo en perfil `dev`)
- **Build:** Maven
- **Contenedores:** Docker / Docker Compose
- **Pruebas:** JUnit 5, Mockito, Testcontainers (PostgreSQL), Spring Boot Test

## Arquitectura general

Aplicación cliente-servidor **stateless**. El backend se organiza por módulos de
negocio, cada uno con sus capas `controller → service → repository` y sus DTOs.

```text
Frontend (Angular)  ──HTTPS/JSON──▶  TF-backEvent (Spring Boot)
                                          │
        ┌───────────────┬─────────────────┼──────────────────┬───────────────┐
        ▼               ▼                 ▼                  ▼               ▼
   PostgreSQL     MercadoPago        Servidor SMTP      Tareas programadas
   (persistencia)  (pagos/OAuth)     (emails)          (reconciliación,
                                                        entrega, cierre)
```

**Autenticación:** el backend emite dos tokens:

- `accessToken` (JWT, vida corta, por defecto 30 min) → se envía en cada request
  como `Authorization: Bearer <token>`.
- `refreshToken` (UUID, vida larga, por defecto 7 días) → se usa en
  `POST /api/v1/auth/refresh` para obtener un nuevo access token sin re-loguear.

## Estructura del proyecto

```text
src/main/java/org/gcrel/tfbackevent/
├── auth/       → registro, login, verificación, JWT, emails
├── event/      → CRUD de eventos, publicación, ventana de acceso
├── payment/    → compra de tickets, pagos, MercadoPago, webhooks, entrega, QR
├── access/     → validación de QR, acceso virtual, controladores
├── model/      → entidades JPA y enums del dominio
└── common/     → seguridad, manejo global de errores y configuración transversal
```

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [docs/api.md](docs/api.md) | Referencia completa de endpoints, DTOs, códigos de estado y errores. |
| [docs/architecture.md](docs/architecture.md) | Arquitectura, modelo de datos y flujos principales. |
| [docs/setup.md](docs/setup.md) | Instalación y ejecución local (Docker y Maven). |
| [docs/deployment.md](docs/deployment.md) | Despliegue, perfiles `dev`/`prod` y variables de entorno. |
| [docs/decisions.md](docs/decisions.md) | Decisiones de diseño y limitaciones conocidas. |

## Inicio rápido

```bash
# 1. Copiar la plantilla de variables de entorno y completarla
#    (como mínimo, JWT_SECRET_KEY: `openssl rand -base64 32`; ver docs/setup.md)
cp .env.template .env

# 2. Levantar base de datos + backend con Docker Compose
docker compose up --build
```

La API queda disponible en `http://localhost:8080` a través de Nginx, que además
sirve las imágenes de los eventos. El backend también responde directo en
`http://localhost:8085`, y Swagger UI (perfil `dev`) está en
`http://localhost:8085/swagger-ui.html`.

Para ejecución local con Maven, datos de prueba y configuración detallada, ver
[docs/setup.md](docs/setup.md).

## Estado del proyecto

Proyecto académico en desarrollo activo. La mayoría de los servicios de negocio y
todos los controladores tienen pruebas unitarias y de integración (la
reconciliación de pagos, `PaymentRecoveryService`, aún no); las consultas
críticas (órdenes huérfanas, desvinculación de MercadoPago y validación concurrente de QR) se prueban contra
PostgreSQL con Testcontainers. Las limitaciones conocidas están en
[docs/decisions.md](docs/decisions.md#limitaciones-conocidas).
