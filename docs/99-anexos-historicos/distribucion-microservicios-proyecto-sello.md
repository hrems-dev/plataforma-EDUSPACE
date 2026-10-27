# Proyecto Sello — Sistema de Gestión Escolar sobre arquitectura pagatu

Distribución de trabajo en equipo (3 personas) y especificación técnica de cada microservicio.

---

## 1. Resumen de la arquitectura

El sistema tiene **7 microservicios propios** (1 dado por la base del curso + 6 nuevos) y una capa de **infraestructura compartida** que se construye una sola vez y sirve a todos.

| Capa | Componentes | Quién la construye |
|---|---|---|
| Infraestructura | Config Server, Eureka, Gateway, Kafka, Prometheus/Loki/Grafana | Equipo completo (S1-S4, colaborativo) |
| Seguridad base | `auth-ms` | Equipo completo (S7), luego cada quien lo consume |
| Microservicios de dominio | `academico-ms`, `matricula-ms`, `pago-ms`, `examenes-ms`, `tareas-ms`, `notificaciones-ms` | 2 por persona (ver sección 2) |
| Frontend | `pagatu-ng` (Angular) | Integración conjunta en S11 |

**Regla de oro:** cada microservicio tiene su propia base de datos. Ningún servicio lee ni escribe directo en la base de otro — toda comunicación entre servicios es por **Feign (síncrono)** o **Kafka (asíncrono)**.

---

## 2. Distribución del equipo

| Integrante | Microservicios a cargo | Balance de carga |
|---|---|---|
| **Persona 1** | `academico-ms` + `notificaciones-ms` | 1 pesado (monolito modular, muchos módulos) + 1 liviano (consumidor de eventos) |
| **Persona 2** | `matricula-ms` + `tareas-ms` | 1 medio-alto (saga/concurrencia) + 1 medio (archivos) |
| **Persona 3** | `pago-ms` + `examenes-ms` | 1 medio (canal externo) + 1 medio-alto (concurrencia) |

Cada persona es responsable de que **su** microservicio cumpla, para las sesiones que correspondan: Config Client, registro en Eureka, ruteo por Gateway, resiliencia (Feign + Circuit Breaker donde aplique), seguridad vía JWT (Resource Server), mensajería (si publica o consume eventos), observabilidad (`/actuator/health`, métricas, logs) y persistencia con RLS por `tenant_id`.

`auth-ms` y la infraestructura (Config, Eureka, Gateway, Kafka, observabilidad) se construyen **entre los tres** en las primeras sesiones (S1-S4 y S7), replicando el patrón que da el curso — no cuentan dentro del reparto de "2 por persona" porque son la base que todos comparten y defienden en conjunto.

---

## 3. Detalle por microservicio

### `auth-ms` (base del curso — equipo completo)

- **Objetivo:** autenticación y autorización del sistema. Emite JWT y valida roles para que el Gateway actúe como Resource Server.
- **Base de datos:** `auth_db` (PostgreSQL)
- **Entidades principales:** `users` (email, password_hash, rol: admin/docente/alumno/padre, tenant_id), `refresh_tokens`
- **Eventos:** no publica ni consume (servicio síncrono, puerta de entrada)
- **Dependencias:** ninguna — es la base que todos los demás consumen para validar el JWT
- **Responsable:** equipo completo

---

### `academico-ms` (Persona 1) — monolito modular

- **Objetivo:** núcleo del sistema. Centraliza todo lo que **no** tiene un pico de tráfico propio: datos del alumno, estructura académica, horario, asistencia, calificaciones consolidadas (boletín), docencia y configuración general.
- **Base de datos:** `academico_db` (PostgreSQL, un solo esquema, módulos separados por tablas)
- **Módulos internos / entidades principales:**
  - Alumno y guardianes: `students`, `guardians`, `student_documents`
  - Estructura académica: `academic_years`, `education_levels`, `grades`, `sections`, `subjects`, `grade_subjects`
  - Horario: `schedules`, `rooms`, `schedule_conflicts`
  - Asistencia: `attendance_records`, `teacher_attendance`
  - Calificaciones (boletín consolidado): `grade_entries`, `report_cards`
  - Docencia: `teachers`, `teacher_assignments`, `teacher_leave_requests`, `payroll`
  - Recursos menores (sin pico de tráfico propio): biblioteca, transporte, salud/MIS — como tablas simples dentro del mismo esquema
- **Eventos que publica:** `boletin.generado`, `asistencia.falta_registrada`
- **Eventos que consume:** `tarea.calificada` (de `tareas-ms`), `examen.calificado` (de `examenes-ms`) — para armar el boletín sin que esos servicios escriban directo en `academico_db`
- **Dependencias:** ninguna hacia otros servicios de dominio (es la fuente de verdad que los demás consultan vía Feign: costo real de matrícula, catálogo de secciones/materias, validación de docente)
- **Responsable:** Persona 1

---

### `matricula-ms` (Persona 2)

- **Objetivo:** proceso de matrícula. Se separa por **escala**: en periodo de matrícula hay concurrencia masiva, mientras el resto del sistema tiene tráfico normal.
- **Base de datos:** `matricula_db` (PostgreSQL)
- **Entidades principales:** `enrollments`, `enrollment_documents`, `waitlist`
- **Eventos que publica:** `matricula.creada` (dispara el cargo en `academico-ms` y notifica), `matricula.confirmada` (cuando `pago-ms` confirma el pago)
- **Eventos que consume:** `pago.confirmado` (de `pago-ms`)
- **Dependencias síncronas (Feign + Circuit Breaker):** consulta a `academico-ms` cupos, costo estimado y validez de sección antes de matricular
- **Punto de resiliencia a sustentar:** ¿qué pasa si `pago-ms` nunca confirma? — la matrícula debe quedar en estado `pending_payment` con expiración, no colgada indefinidamente (tema para S9, consistencia distribuida)
- **Responsable:** Persona 2

---

### `pago-ms` (Persona 3)

- **Objetivo:** canal delgado de cobro. Se separa por **aislamiento de canal**: intermedia con la pasarela externa sin decidir montos ni descuentos (eso vive en `academico-ms`).
- **Base de datos:** `pago_db` (PostgreSQL)
- **Entidades principales:** `invoices`, `payments`, `discounts`
- **Eventos que publica:** `pago.confirmado`, `pago.vencido`
- **Eventos que consume:** `matricula.creada` (para saber qué cobrar)
- **Dependencias:** pasarela de pago externa (Stripe/PayPal/transferencia); consulta a `academico-ms` el monto real antes de confirmar
- **Responsable:** Persona 3

---

### `examenes-ms` (Persona 3)

- **Objetivo:** aplicación de exámenes con temporizador. Se separa porque un examen masivo (cientos de alumnos rindiendo a la vez) degradaría horario/asistencia/notas si viviera dentro de `academico-ms`.
- **Base de datos:** `examenes_db` (PostgreSQL)
- **Entidades principales:** `exams`, `exam_questions`, `exam_attempts`, `exam_answers`
- **Eventos que publica:** `examen.calificado`
- **Eventos que consume:** ninguno (recibe llamadas síncronas del alumno vía Gateway)
- **Dependencias síncronas:** valida contra `academico-ms` que el alumno esté matriculado en la sección/materia del examen
- **Punto crítico a sustentar:** rate limiting y pool de conexiones propio, para que un pico de examen no tumbe su propia base de datos
- **Responsable:** Persona 3

---

### `tareas-ms` (Persona 2)

- **Objetivo:** entrega de tareas con archivos adjuntos. Se separa por el pico de envíos cerca de la fecha límite y por el manejo de almacenamiento externo (S3/MinIO), un patrón de carga distinto al CRUD normal.
- **Base de datos:** `tareas_db` (PostgreSQL) + almacenamiento de objetos (S3/MinIO) para los archivos
- **Entidades principales:** `assignments`, `assignment_submissions`, `submission_files`
- **Eventos que publica:** `tarea.calificada`, `tarea.entregada`
- **Eventos que consume:** ninguno
- **Dependencias síncronas:** valida contra `academico-ms` que el alumno esté matriculado en la materia
- **Responsable:** Persona 2

---

### `notificaciones-ms` (Persona 1)

- **Objetivo:** canal de comunicación. Se separa por **fan-out**: cuando se publica una nota, se confirma matrícula, vence un pago o se abre un examen, se dispara notificación a cientos de padres a la vez — si esto corriera síncrono dentro de otro servicio, cada publicación masiva lo bloquearía. Absorbe también mensajería 1:1 y foros.
- **Base de datos:** `notificaciones_db` (PostgreSQL)
- **Entidades principales:** `notifications`, `messages`, `announcements`
- **Eventos que consume:** `boletin.generado`, `asistencia.falta_registrada`, `matricula.creada`, `matricula.confirmada`, `pago.vencido`, `tarea.calificada`, `examen.calificado`
- **Eventos que publica:** ninguno (es consumidor terminal)
- **Dependencias:** proveedor externo de email/SMS/push
- **Punto crítico a sustentar:** manejo de reintentos y dead-letter queue si el proveedor externo falla — no se deben perder notificaciones
- **Responsable:** Persona 1

---

## 4. Matriz de eventos Kafka

| Tópico | Publica | Consume |
|---|---|---|
| `matricula.creada` | `matricula-ms` | `pago-ms`, `notificaciones-ms` |
| `pago.confirmado` | `pago-ms` | `matricula-ms` |
| `pago.vencido` | `pago-ms` | `notificaciones-ms` |
| `examen.calificado` | `examenes-ms` | `academico-ms`, `notificaciones-ms` |
| `tarea.calificada` | `tareas-ms` | `academico-ms`, `notificaciones-ms` |
| `tarea.entregada` | `tareas-ms` | `notificaciones-ms` |
| `boletin.generado` | `academico-ms` | `notificaciones-ms` |
| `asistencia.falta_registrada` | `academico-ms` | `notificaciones-ms` |

---

## 5. Puertos sugeridos (siguiendo convención pagatu: D=dev, P=producción local)

| Servicio | Puerto DEV | Puerto PROD local |
|---|---|---|
| Config Server | 18888 | 28888 |
| Eureka | 18761 | 28761 |
| Gateway | 18080 | 28082 |
| `auth-ms` | 18081 | 28081 |
| `academico-ms` | 18082 | 28082... *(ajustar para no chocar con Gateway; usar 18090+)* |
| `matricula-ms` | 18083 | 28083 |
| `pago-ms` | 18084 | 28084 |
| `examenes-ms` | 18085 | 28085 |
| `tareas-ms` | 18086 | 28086 |
| `notificaciones-ms` | 18087 | 28087 |
| Kafka broker | 41092 | 29092 |
| Kafka UI | 41085 | 28085 |
| Prometheus | 19090 | 29090 |
| Loki | 13100 | 23100 |
| Grafana | 13000 | 23000 |
| Angular (`pagatu-ng`) | 4200 | — |

> Nota: revisar colisión de puertos antes de dockerizar (varios microservicios de dominio comparten rango con Kafka UI en el ejemplo de pagatu original) — asignar rangos únicos por servicio dentro de `application.yml`/Config Server.

---

## 6. Checklist de entregables por persona (según el avance del curso)

| Sesión | Todos | Persona 1 | Persona 2 | Persona 3 |
|---|---|---|---|---|
| S1-S2 | Servicio base + Config Server (colaborativo sobre `academico-ms` como referencia) | — | — | — |
| S3-S4 | Eureka + Gateway + balanceo | Registra `academico-ms` y `notificaciones-ms` | Registra `matricula-ms` y `tareas-ms` | Registra `pago-ms` y `examenes-ms` |
| S6 | — | Feign desde `notificaciones-ms` no aplica (es consumidor); validar Feign de `academico-ms` hacia ningún otro | Feign `matricula-ms → academico-ms` + Circuit Breaker | Feign `pago-ms/examenes-ms → academico-ms` + Circuit Breaker |
| S7 | `auth-ms` colaborativo | Protege `academico-ms`/`notificaciones-ms` con JWT | Protege `matricula-ms`/`tareas-ms` con JWT | Protege `pago-ms`/`examenes-ms` con JWT |
| S8-S9 | — | Publica/consume eventos de `academico-ms` y `notificaciones-ms`, valida idempotencia | Publica `matricula.creada`, maneja saga de `pending_payment` | Publica `pago.confirmado`/`pago.vencido`, publica `examen.calificado` |
| S10 | Observabilidad extendida a todos los servicios | Paneles de `academico-ms`/`notificaciones-ms` | Paneles de `matricula-ms`/`tareas-ms` | Paneles de `pago-ms`/`examenes-ms` |
| S11 | Integración Angular vía Gateway (colaborativo) | — | — | — |
| S13-S16 | Validación end-to-end, documentación y sustentación individual | Defiende sus 2 microservicios | Defiende sus 2 microservicios | Defiende sus 2 microservicios |
