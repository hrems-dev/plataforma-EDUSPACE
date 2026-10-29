# Equipo y reparto de microservicios

EduSpace son **10 microservicios con base de datos** más la infraestructura compartida. El reparto sale de
`docs/02-hermes-…/informe-hermes-ya-hecho.md` (§1.6) y de la tabla del `README.md` raíz; el estado y las cifras, del código actual.

| Persona | Microservicios | Documento |
|---|---|---|
| **Hermes** | `planificacion-ms` · `alumnos-ms` · `notificaciones-ms` (y `auth-ms` como infraestructura) | [hermes.md](hermes.md) |
| **Andre Fabricio** | `matricula-ms` · `asistencia-ms` · `tareas-ms` | [andre.md](andre.md) |
| **Witmer** | `pago-ms` · `examenes-ms` · `calificaciones-ms` | [witmer.md](witmer.md) |
| Todo el equipo | Config Server, Eureka, Gateway, Kafka, MinIO, observabilidad, `auth-ms`/Keycloak | §3 de este archivo |

## 1. Cómo se conectan los servicios (lo que todos deben saber)

```
            ┌────────────┐    JWT     ┌──────────────┐  lb://  ┌───────────────┐
 Frontend ─►│  Gateway   │───────────►│ 10 servicios │◄───────►│ Eureka/Config │
 (Angular)  │   :18080   │            │ :8090 – :8099│         └───────────────┘
            └────────────┘            └──────┬───────┘
                                             │ eventos (Kafka, patrón outbox)
                                             ▼
                                   otros servicios / notificaciones
```

- **Entrada única:** el Gateway valida el JWT (Keycloak en DEV, `auth-ms` en PROD) y reenvía a cada servicio. Cada servicio vuelve a validarlo.
- **Quién es quién:** el token trae el claim `usuarioId`; ningún servicio asume `usuarioId == alumnoId/docenteId`. Se traduce con los
  endpoints `/internal/...` de planificación y alumnos.
- **Llamadas síncronas (Feign):** solo hacia `/internal/**`, con el header `X-Service-Token`.
- **Eventos (Kafka):** sobre JSON plano (`eventId`, `eventType`, `occurredAt`, `version`, `traceId` + campos de negocio), publicados con *outbox*
  y consumidos con idempotencia (`eventos_procesados`) y DLT (`<tópico>.DLT`).
- **Base de datos por servicio:** PostgreSQL + Flyway, `ddl-auto: validate`. Nadie lee la base de otro servicio.
- **Réplicas locales:** `matriculas_local` (en asistencia, tareas, examenes, calificaciones, pago) se llena con los eventos `matricula.*`, para no
  consultar a matrícula en cada petición.

### Eventos del sistema

| Evento | Lo publica | Lo consumen |
|---|---|---|
| `matricula.creada / confirmada / trasladada / retirada / anulada` | matricula | pago, asistencia, tareas, examenes, calificaciones, notificaciones |
| `pago.confirmado` · `pago.anulado` | pago | matricula (y notificaciones el confirmado) |
| `pago.por-vencer` · `pago.vencido` | pago (jobs) | notificaciones |
| `asistencia.falta-registrada` · `justificacion.resuelta` | asistencia | notificaciones |
| `tarea.publicada` · `tarea.recordatorio` | tareas | notificaciones |
| `tarea.calificada` | tareas | calificaciones, notificaciones |
| `tarea.entregada` | tareas | nadie todavía |
| `examen.publicado` | examenes | notificaciones |
| `examen.calificado` | examenes | calificaciones, notificaciones |
| `boletin.generado` | calificaciones | notificaciones |
| `horario.publicado` | planificacion | notificaciones |
| `alumno.registrado` · `alumno.estado-cambiado` · `anio.activado` · `periodo.cambiado` | alumnos / planificacion | nadie todavía |

## 2. Mapa de dependencias

| Servicio | Puerto | Base (DEV) | Llama a (Feign) | Escucha |
|---|---|---|---|---|
| planificacion-ms | 8091 | 15441 | — (fuente de verdad) | — |
| alumnos-ms | 8092 | 15442 | MinIO | — |
| notificaciones-ms | 8099 | 15449 | alumnos | 15 tópicos |
| matricula-ms | 8093 | 15443 | planificacion, alumnos, pago | `pago.confirmado`, `pago.anulado` |
| asistencia-ms | 8094 | 15444 | alumnos, planificacion, matricula | `matricula.*` |
| tareas-ms | 8098 | 15448 | planificacion, matricula, alumnos | `matricula.*` |
| pago-ms | 8096 | 15446 | alumnos, planificacion | `matricula.*` |
| examenes-ms | 8097 | 15447 | alumnos, planificacion | `matricula.*` |
| calificaciones-ms | 8095 | 15445 | alumnos, planificacion | `matricula.*`, `tarea.calificada`, `examen.calificado` |
| auth-ms | 8090 | 15440 | — | — |

## 3. Infraestructura compartida (de todos)

| Pieza | Dónde | Qué hace |
|---|---|---|
| Config Server | `infra/eduspace-config` (:18888) | Configuración de todos en `config-repo/<servicio>-dev.yml` / `-prod.yml` |
| Eureka | `infra/eduspace-eureka` (:18761) | Registro; el Gateway enruta con `lb://` |
| Gateway | `infra/eduspace-gateway` (:18080) | Valida JWT, CORS, límite de peticiones (429), `X-Trace-ID` |
| Kafka, MinIO, Mailpit | `kafka/`, `tools/e2e/levantar.sh` | Mensajería, archivos, correo de pruebas |
| Observabilidad | `obs/` | Prometheus, Loki, Promtail, Grafana |
| Keycloak / auth-ms | `infra/keycloak/`, `services/auth-ms` | Emisor de tokens (Keycloak en DEV, `auth-ms` en PROD) |
| Frontend | `frontend/` | Angular 22 (ver `frontend/README.md`); cada documento personal indica qué pantallas corresponden a sus servicios |

## 4. Cómo levantar y probar (para cualquiera)

```bash
tools/e2e/compilar.sh                 # una vez o tras cambiar código
tools/e2e/levantar.sh                 # todo en local (Podman); --sin-reset conserva datos; --auth-propio para los escenarios e2e
tools/e2e/correr_todo.sh              # 6 escenarios de punta a punta (con --auth-propio)
cd services/<servicio>-ms && ./mvnw test      # pruebas de un servicio (las de integración usan Testcontainers/Docker)
cd frontend && npx ng test --watch=false      # pruebas del frontend
```

Más detalle: `README.md` (raíz), `tools/e2e/README.md`, `docs/05-integracion-y-correcciones.md`, `docs/08-avance-2026-10-28.md`, `docs/09-espacios-de-trabajo.md`.

## 5. Convenciones que aplican a todos

- Paquete base: Hermes `pe.edu.eduspace.*`; Andre y Witmer `pe.edu.upeu.eduspace.*` (unificarlo sigue pendiente).
- Errores con el formato de cada `GlobalExceptionHandler`; validación de entrada con Bean Validation (400 con detalle).
- Autorización: `@PreAuthorize` (alumnos, calificaciones) o reglas en `SecurityConfig` (el resto); `/internal/**` exige `X-Service-Token`.
- Todo cambio de datos que otros servicios necesiten pasa por un evento (outbox), no por acceso directo a la base.
