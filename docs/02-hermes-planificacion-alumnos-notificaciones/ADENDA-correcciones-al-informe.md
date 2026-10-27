# Adenda al informe de Hermes (planificación · alumnos · notificaciones)

El informe `informe-hermes-ya-hecho.md` **no se modifica**. Esta adenda recoge lo que la revisión de huecos y cuellos de botella (informe general, §4) obliga a **agregar o cambiar**. Todo es aditivo salvo A1 y A8 (nombres).

Índice: A1 sobre de eventos · A2 endpoints internos de planificación · A3 endpoints internos de alumnos · A4 notificaciones V2 · A5 eventos nuevos que consume notificaciones · A6 caché y circuit breaker · A7 cambios en el Gateway/seguridad · A8 correcciones de nombres y detalles.

---

## A1. Sobre estándar en todos los eventos
Los eventos de este informe (§10.1: `alumno.registrado`, `alumno.estado-cambiado`, `horario.publicado`, `anio.activado`, `periodo.cambiado`) y todos los que consume `notificaciones-ms` llevan, además de sus campos:

`eventId` (UUID) · `eventType` · `occurredAt` · `version` (1) · `traceId`.

- Los productores (`PlanificacionEventProducer`, `AlumnoEventProducer`) generan el `eventId` al crear el evento.
- `notificaciones-ms` descarta un evento si su `eventId` ya está en `eventos_procesados` (tabla de la V2, A4). El índice único `uk_notif_evento` sigue como segunda red de seguridad.

## A2. `planificacion-ms` — endpoints internos nuevos (sin pasar por el Gateway)
Los consumen asistencia, tareas, exámenes, calificaciones y matrícula. Deben ser **livianos, sin paginación pesada y rápidos**.

| Endpoint | Devuelve | Lo usa |
|---|---|---|
| `GET /internal/docentes/usuario/{usuarioId}` | docenteId, nombre, estado | todos los que autorizan docentes |
| `GET /internal/docentes/usuario/{usuarioId}/asignaciones?anioId=` | lista de (seccionId, cursoId, asignacionId) | asistencia, tareas, exámenes, calificaciones |
| `GET /internal/asignaciones/validar?docenteId=&seccionId=&cursoId=` | `true/false` | las mismas (validación puntual) |
| `GET /internal/secciones/{id}` | id, nombre, gradoId, nivelId, anioId, capacidad, turno, tutorDocenteId | matrícula, asistencia |
| `GET /internal/secciones?anioId=&gradoId=` | lista corta | matrícula |
| `GET /internal/anios/activo` | id, nombre, fechas | matrícula, pago |
| `GET /internal/periodos?anioId=` y `/{id}` | número, nombre, fechas, estado | calificaciones, tareas, exámenes |
| `GET /internal/cursos/{id}` y `GET /internal/cursos?ids=` | id, código, nombre | notificaciones (textos), calificaciones |
| `GET /internal/grados/{id}` y `/niveles/{id}` | nombre, código de nivel | pago, calificaciones |
| `GET /internal/feriados/es-habil?fecha=` | `true/false` | asistencia |

Cambios de código necesarios:
- Nuevo `InternalController` (paquete `controller`) y DTO mínimos en `dto`.
- `AsignacionService` expone la consulta por `usuarioId` (join docente → asignaciones).
- En `SecurityConfig`: `/internal/**` permitido (ya previsto); el Gateway **no** lo enruta.
- Índices ya existentes cubren estas consultas (`idx_asignaciones_docente`, `uk_asignacion`); revisar `docentes.usuario_id` (único).

## A3. `alumnos-ms` — endpoints internos nuevos

| Endpoint | Devuelve | Lo usa |
|---|---|---|
| `GET /internal/alumnos/{id}` (ya existía) | id, código, nombre completo, estado | matrícula, notificaciones |
| `GET /internal/alumnos/{id}/contactos` (ya existía) | alumno + apoderados con usuarioId, email, teléfono, esResponsablePago | notificaciones, pago |
| **`GET /internal/alumnos/{id}/expediente-completo`** | `{completo: bool, faltantes: [tipos]}` según documentos requeridos y verificados | matrícula |
| **`GET /internal/alumnos/usuario/{usuarioId}`** | alumnoId (si el usuario es un alumno) | todos (permiso "solo lo propio") |
| **`GET /internal/apoderados/usuario/{usuarioId}/alumnos`** | lista de alumnoId e info corta de los hijos | asistencia, tareas, calificaciones, pago, matrícula |
| **`GET /internal/alumnos/lote?ids=1,2,3`** | nombres de varios alumnos en una llamada | listados que necesiten nombres |
| **`GET /internal/alumnos/{id}/responsable-pago`** | usuarioId, nombre del apoderado responsable de pago | pago |

Cambios de código:
- `InternalController` ampliado; `ContactosService` y nuevo `ExpedienteService` (regla de "expediente completo": partida de nacimiento, DNI y ficha SIAGIE verificados; configurable).
- Repositorio: consulta de hijos por `apoderado.usuario_id` y de alumno por `usuario_id` (ya hay índices únicos).
- **Los eventos `alumno.registrado` y `alumno.estado-cambiado` no deben incluir salud, documentos ni DNI** (privacidad de menores).

## A4. `notificaciones-ms` — migración V2 (entregada)
Archivo: `sql/notificaciones-ms/V2__shedlock_e_idempotencia_y_plantillas.sql`. Agrega:
- `shedlock` → el `ReintentoScheduler` se ejecuta en **una sola** réplica. Nuevo `config/ShedLockConfig` y anotación de bloqueo en el job.
- `eventos_procesados` → idempotencia por `eventId`: el consumidor consulta/inserta en la misma transacción antes de crear notificaciones.
- 5 plantillas nuevas: `MATRICULA_CONFIRMADA`, `MATRICULA_ANULADA`, `PAGO_POR_VENCER`, `EXAMEN_PUBLICADO`, `JUSTIFICACION_RESUELTA`.
- Job adicional de limpieza (diario, bloqueado con ShedLock): borrar `eventos_procesados` con más de 30 días.

Para ejecutar V1 y luego V2 con Flyway no hay que cambiar nada en V1.

## A5. Tópicos adicionales que consume `notificaciones-ms`
Se suman a los 9 del informe (§10.2):

| Tópico | Productor | Plantilla | Destinatarios |
|---|---|---|---|
| `matricula.confirmada` | matricula | MATRICULA_CONFIRMADA | alumno + apoderados |
| `matricula.anulada` | matricula | MATRICULA_ANULADA | apoderado principal / responsable de pago |
| `pago.por-vencer` | pago | PAGO_POR_VENCER | responsable de pago |
| `examen.publicado` | examenes | EXAMEN_PUBLICADO | `alumnoIds[]` del evento + sus apoderados |
| `justificacion.resuelta` (opcional) | asistencia | JUSTIFICACION_RESUELTA | apoderado solicitante |

Cambios: nuevos métodos de escucha en `NotificacionEventConsumer`, clases en `event/` y mapeo tópico → plantilla en `NotificacionOrquestador`.

Regla de destinatarios (aclaración): si el evento ya trae `alumnoIds[]` (tarea/examen publicados), se resuelven los contactos **en lote** (una llamada por alumno con caché de 5 min, o `lote`), no una por mensaje.

## A6. Caché y circuit breaker de `alumnos-ms` en `notificaciones-ms`
- Caché local (Caffeine) de contactos por `alumnoId` con vencimiento de 5 min y tamaño máximo.
- Circuit breaker `alumnosClient` como ya se definió; cuando está abierto, la notificación queda **PENDIENTE** y `ReintentoScheduler` la retoma.
- Métrica visible en Grafana: estado del breaker y cantidad de notificaciones PENDIENTE.

## A7. Seguridad y Gateway
- El token incluye el claim `usuarioId`; `alumnos-ms` lo usa para el filtro "solo lo propio" (alumno) y "solo mis hijos" (apoderado).
- Rutas del Gateway: se mantienen las 4 del informe; la tabla completa de 10 rutas está en el informe de infraestructura §7.4.
- CORS se configura en el Gateway (no en cada servicio).

## A8. Correcciones menores al informe
| Dónde | Corrección |
|---|---|
| §12 (tablas de notificaciones) | `notificaciones_db` tiene **5** tablas en V1 y **7** con la V2 (el resto de conteos del informe es correcto). |
| §10.2 | Nombre del tópico de asistencia: `asistencia.falta-registrada` (con guion). |
| §9.7 | `horario.publicado` solo notifica a docentes en el MVP (confirmado). |
| §14 punto 4 | Decisión cerrada: los eventos llevan `alumnoId` y se usa Feign con caché y circuit breaker; las listas `alumnoIds[]` ya vienen en tarea/examen publicados. |

## A9. Pruebas adicionales para Hermes
- El mismo `eventId` entregado dos veces genera una sola notificación.
- Dos réplicas de `notificaciones-ms`: el reintento corre una vez por ciclo.
- `alumnos-ms` caído: contactos desde caché; sin caché → PENDIENTE; al volver → ENVIADA.
- Los endpoints `/internal/**` no son alcanzables por el Gateway (devuelve 404).
- Un APODERADO no obtiene datos de hijos ajenos por ningún endpoint.
