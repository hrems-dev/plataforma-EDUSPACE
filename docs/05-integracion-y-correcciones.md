# Integración, correcciones y construcción de la parte de Andre

Fecha: 2026-10-16 · Alcance: corrección de los servicios de Witmer, construcción de `matricula-ms`, `asistencia-ms` y
`tareas-ms` según `docs/03-andre-matricula-asistencia-tareas/` y verificación de los 9 servicios (pruebas y corrida
de punta a punta). Guía de arquitectura: pagatu.

## 1. Contratos que quedaron fijados

### 1.1 Sobre de eventos (informe general §5.1, adenda A1)

Los campos de negocio van **planos**, junto a `eventId`, `eventType`, `occurredAt`, `version` y `traceId`. Witmer los
publicaba anidados en `payload`; `notificaciones-ms` (y los consumidores de Andre) leen el formato plano, así que
`pago.confirmado`, `pago.vencido`, `examen.*` y `boletin.generado` no habrían notificado a nadie.

Cada servicio de Witmer y de Andre tiene `EventoSobre` con `escribir(mapper, sobre)` y `leer(mapper, json, Clase)`.
`leer` también acepta el formato anidado antiguo (por si hay mensajes viejos en Kafka). Serialización: **texto JSON
(`StringSerializer`/`StringDeserializer`)** en lugar de `JsonSerializer` con cabeceras de tipo, y **Jackson 3
(`tools.jackson`)** como en los servicios de Hermes.

### 1.2 Eventos producidos y consumidos

| Evento | Productor | Consumidores | Campos de negocio |
|---|---|---|---|
| `matricula.creada/.confirmada/.trasladada/.retirada/.anulada` | matricula-ms (outbox) | pago, asistencia, tareas, examenes, calificaciones, notificaciones | matriculaId, alumnoId, alumnoNombre, anio, anioId, nivelId, gradoId, gradoNombre, seccionId, seccionNombre, estado, tipo, seccionAnteriorId, motivo, responsablePagoUsuarioId, responsablePagoNombre |
| `pago.confirmado` | pago-ms (outbox) | matricula-ms, notificaciones | pagoId, alumnoId, matriculaId, cuotaIds, conceptoTipo, monto, concepto |
| `pago.anulado` | pago-ms (outbox) | matricula-ms | pagoId, alumnoId, motivo, **matriculaId, conceptoTipo, monto** (se agregaron; sin ellos matrícula no podía filtrar por concepto) |
| `pago.por-vencer`, `pago.vencido` | pago-ms (jobs) | notificaciones | cuotaId, alumnoId, matriculaId, concepto, monto, saldo, fechaVencimiento |
| `asistencia.falta-registrada` | asistencia-ms | notificaciones | asistenciaId, asistenciaDetalleId, alumnoId, matriculaId, seccionId, fecha |
| `justificacion.resuelta` | asistencia-ms | notificaciones | justificacionId, alumnoId, matriculaId, resultado, fechaDesde, fechaHasta |
| `tarea.publicada` | tareas-ms | notificaciones | tareaId, seccionId, cursoId, cursoNombre, titulo, fechaEntrega, alumnoIds |
| `tarea.entregada` | tareas-ms | — | tareaId, entregaId, alumnoId, matriculaId, seccionId, version, esTardia |
| `tarea.calificada` | tareas-ms | calificaciones, notificaciones | tareaId, entregaId, alumnoId, matriculaId, seccionId, cursoId, **periodoId**, titulo, **nota (0–20)**, puntajeMaximo, puntajeObtenido |
| `tarea.recordatorio` | tareas-ms (job) | notificaciones (plantilla `TAREA_RECORDATORIO`, `V4`) | igual que `tarea.publicada`, con `alumnoIds` = quienes no han entregado |
| `examen.publicado` / `examen.calificado` | examenes-ms | notificaciones / calificaciones, notificaciones | `examen.calificado` ahora incluye `cursoNombre` |
| `boletin.generado` | calificaciones-ms | notificaciones | boletinId, alumnoId, matriculaId, periodoId, periodoNombre |

### 1.3 Endpoints internos que usan los servicios

`/internal/anios/{id}` se **agregó a `planificacion-ms`** (pago-ms lo necesita para el año calendario de las cuotas).
`matricula-ms` expone `/internal/matriculas/seccion/{id}` y `/internal/matriculas/alumno/{id}?anioId=`;
`pago-ms` ya exponía `/internal/pagos/alumno/{id}/deuda` (lo usa la ratificación).

### 1.4 Identidad

Ningún servicio asume `usuarioId == alumnoId/docenteId`, ni usa `1L` por defecto. El claim `usuarioId` es obligatorio
(sin él: 403) y se traduce con `/internal/docentes/usuario/{id}`, `/internal/alumnos/usuario/{id}` y
`/internal/apoderados/usuario/{id}/alumnos`. Un 404 de esos endpoints significa "el usuario no es docente/alumno" y se
trata como 403, no como error del servidor. Roles: `realm_access.roles` (y, por tolerancia, `roles`).

## 2. Correcciones a los servicios de Witmer

| Servicio | Problema encontrado | Corrección |
|---|---|---|
| todos | Sobre anidado y `JsonSerializer` con cabeceras de tipo (los `@KafkaListener(String)` no funcionaban con Kafka real) | Sobre plano + serialización de texto + Jackson 3 |
| pago | `toResponse(Pago)` emitía un comprobante en **cada lectura** (consumía correlativos, duplicaba archivos) | Solo consulta el comprobante existente |
| pago | Año de las cuotas = `anioId` (fechas del año 1) | Año calendario desde `planificacion-ms` (`/internal/anios/{id}`); si no responde, el año actual |
| pago | Pensión: monto por cuota sin reparto | **El monto de la tarifa es el total de la pensión**; se reparte en `num_cuotas`, redondeo a 2 decimales y la diferencia en la última cuota (informe, `GeneradorCuotasService`) |
| pago | Sin tarifa solo había un `log.error` | Tabla `alertas_admin` (`V2`), `GET/PATCH /api/v1/pagos/alertas`; al crear la tarifa se generan las cuotas pendientes y se resuelve la alerta |
| pago | Retiro: las cuotas PARCIAL no se trataban | Alerta `CUOTA_PARCIAL` para decisión de administración; VENCIDAS también se anulan |
| pago | **El outbox fallaba al insertar** (`jsonb` ← `varchar`) | `@JdbcTypeCode(SqlTypes.JSON)` |
| pago | **`/mias/cuotas`, `estado-cuenta`, `morosos`, `cuotas` daban 500** (carga perezosa fuera de transacción) | `@Transactional(readOnly = true)` |
| pago | Mora se capitalizaba día a día y solo se calculaba el primer día | Base sin mora acumulada, escala de 10 decimales, y el job recalcula a diario las VENCIDAS sin repetir el evento |
| pago | Comprobante `.txt` | **PDF real** (`PdfTexto`, sin dependencias) |
| pago | Sin `@CircuitBreaker`/`@Cacheable` hacia alumnos | `AlumnosGateway` con breaker, fallback y caché |
| pago | Sin `PUT` de descuentos | `PUT /api/v1/pagos/descuentos/{id}` |
| pago | Contratos Feign distintos a los de Hermes | `AlumnoContactoResponse(apoderadoId, usuarioId, nombre)`, `HijoResumen(alumnoId, codigo, nombreCompleto, estado, parentesco)` |
| examenes | Faltaba `PUT /examenes/{id}` | Edición en BORRADOR, solo el docente dueño |
| examenes | `publicar` no validaba dueño, asignación ni ventana | Valida las tres; `cerrar` verifica dueño y **cierra/califica los intentos abiertos** |
| examenes | `tipo` era `VARCHAR(12)` y `OPCION_MULTIPLE` (15) no cabía → **no se podía crear preguntas** | `VARCHAR(16)` en `V1` |
| examenes | Nada impedía dos intentos `EN_CURSO` | Índice único parcial (`V2__intento_unico_en_curso.sql`) |
| examenes | Cualquiera leía el resultado de otro intento (IDOR); `/mias` buscaba por `usuarioId` | Control de dueño; `/mias` resuelve alumno/hijos |
| examenes | Se podía responder una pregunta ajena al examen | Se valida que pertenezca |
| examenes | `examen.calificado` sin `cursoNombre` | Se agrega |
| calificaciones | Pendientes de asignar **en memoria** (`CopyOnWriteArrayList`) | Tabla `notas_pendientes` (`V2`); se aplican al crear el criterio y al reabrir el periodo |
| calificaciones | Periodo CERRADO solo hacía `warn` | La nota queda `PERIODO_CERRADO` visible al docente y se incorpora al reabrir |
| calificaciones | Validaba asignación con el `usuarioId` como `docenteId` y **fallaba abierto** | `DocenteAutorizacionService`: resuelve el docente; si planificación cae, deniega |
| calificaciones | Registrar notas no validaba asignación docente | Se valida (ADMIN exento) |
| calificaciones | Libreta/boletines sin control de dueño; el alumno no veía su propia libreta | `IdentidadService.exigirAcceso`; `/mias` incluye al propio alumno |
| calificaciones | Boletín `.txt`; contrato `HijoResumen` distinto | PDF; contrato alineado con Hermes |
| calificaciones/pago | Rutas de Spring Security excluían DOCENTE de libreta/boletines pese a la anotación del controlador | Alineado |
| todos | MinIO con credenciales distintas a las de `kafka/compose-dev.yml` | Por defecto `minioadmin/minioadmin` |
| todos | Handler de errores devolvía 500 para JSON mal formado, tipos, duplicados | 400/405/409 según corresponda |
| examenes | Banco de preguntas daba 500 sin filtro de tema (`LOWER(null)` en PostgreSQL) | Texto vacío = sin filtro |
| pago | `GET /cuotas` ignoraba el filtro `alumnoId` | Filtro agregado |
| todos | Sin tiempos de espera en Feign | `connect 2 s` / `read 8 s` |

## 3. Servicios de Andre construidos

Estructura igual a pagatu / Witmer (paquete `pe.edu.upeu.eduspace.<servicio>`): `config`, `client` (Feign + `*Gateway`
con circuit breaker), `entity`, `repository`, `dto`, `service`, `messaging`, `scheduler`, `controller`, `security`.

### 3.1 `matricula-ms` (8093)
Campañas (una ABIERTA por año), cupos con `UPDATE … WHERE ocupados < capacidad` (el último cupo lo gana una sola
solicitud), matrícula con validaciones en el orden del informe, confirmación por `pago.confirmado` (idempotente),
traslado de sección, retiro, anulación, requisitos, ratificación individual/masiva con verificación de deuda,
vencimiento horario de pre-matrículas y outbox (`FOR UPDATE SKIP LOCKED`, espera el acuse de Kafka).
- Se agregó `V2` (secuencia de `MAT-<año>-NNNNNN`).
- Ratificación: `matriculaAnteriorId` (individual) o `seccionOrigenId` (masiva) y `seccionDestinoId` o `gradoDestinoId`
  (elige la primera sección con cupo). Los que fallan se reportan sin detener a los demás.
- `pago.anulado` de matrícula **no revierte**: deja constancia en el historial.

### 3.2 `asistencia-ms` (8094)
Réplica `matriculas_local` por eventos (idempotente y descarta eventos viejos), sesiones DIA/CURSO (idempotentes),
validación de docente asignado / fecha hábil / no futura, registro masivo en una transacción, falta notificada **una
sola vez** por detalle, cierre manual y nocturno, corrección con auditoría, justificaciones (archivo con URL firmada,
aprobación que convierte exactamente las faltas del rango), reportes y alerta de exceso de faltas.
- `V2` agrega `confirmado` y `falta_notificada` a `asistencia_detalle`.
- Los reportes y consultas leen solo la base local: siguen funcionando con `planificacion-ms` caído.

### 3.3 `tareas-ms` (8098)
Tareas BORRADOR→PUBLICADA→CERRADA, material y entregas con URL firmada de MinIO (se valida tipo y tamaño **antes** de
firmar y que la ruta pertenezca al alumno), entrega/reenvío con versión, tardía solo si la tarea lo permite,
calificación individual y masiva (nota convertida a 0–20), devolución, pendientes del alumno y del apoderado,
recordatorio único por tarea y cierre automático de las vencidas sin entrega tardía.
- `V2` agrega `recordatorio_enviado`.
- `tarea.recordatorio` lo consume `notificaciones-ms` (aviso IN_APP a los alumnos y apoderados de quienes no han entregado).

## 4. Otros arreglos que salieron de la corrida real

- **Feign y `LocalDate`:** sin `@DateTimeFormat(iso = DATE)` Feign envía `15/10/26` y `planificacion-ms` responde 400
  (asistencia nunca habría podido abrir una sesión). Corregido; las pruebas con mocks no lo detectan.
- **DEV y Eureka:** los servicios de Witmer/Andre registraban la IP de la máquina (`prefer-ip-address: true`); al
  cambiar de red los demás no los alcanzaban. En `config-repo/*-dev.yml` ahora `hostname: localhost`.
- `alumnos-ms` responde 404 si el usuario no es alumno/apoderado: ahora es 403, ya no 500.
- **Gateway:** las rutas de matrícula, asistencia y tareas se agregaron también a `config-repo/eduspace-gateway-dev.yml`
  (el Config Server pisa el `application.yml` del Gateway).
- `matricula-ms`: el listado con `texto` nulo daba 500 por `LOWER(null)`; mismo patrón que ya había resuelto Hermes.
- DEV: `registry-fetch-interval-seconds: 5` para que un servicio vea a los demás en segundos y no en 30 s tras arrancar.

## 5. Cómo se verificó

1. **Pruebas automáticas** (PostgreSQL 16 real con Testcontainers, Kafka y servicios vecinos simulados): ver `README.md`.
2. **Corrida de punta a punta** (`tools/e2e/`): Config Server + Eureka + los 9 servicios + Gateway + Kafka + MinIO +
   Mailpit, con tokens RS256 reales emitidos por un sustituto de `auth-ms`. Recorre: planificación y alumnos →
   campaña/matrícula/cupos → cobro en caja → confirmación por Kafka → pensiones → réplicas en 4 servicios → asistencia
   (falta, justificación) → tarea (archivo en MinIO, calificación) → examen → criterios, promedio, cierre, boletín PDF
   → libretas → Gateway → traslado y retiro (anula cuotas) → notificaciones y correos.

## 6. `auth-ms` (8090)

Paquete `pe.edu.upeu.eduspace.auth`, base `auth_db` (15440), `V1` entregado sin cambios.
- **Login** `POST /api/v1/auth/login` (usuario o correo): `access_token`, `refresh_token`, `token_type`, `expires_in`, `debe_cambiar_clave`.
  Usuario inexistente y clave errónea dan el **mismo** 401 (y tardan lo mismo); 5 fallos bloquean 15 min (423, aunque la clave sea correcta).
- **JWT RS256** con `sub`, `iss`, `iat`, `exp`, `preferred_username`, `email`, `usuarioId`, `realm_access.roles` (sin `ROLE_`); 30 min.
  JWKS en `/.well-known/jwks.json` (solo parte pública; admite clave anterior para rotación).
- **Claves:** PROD las carga de archivos PEM y **si faltan no arranca**; DEV genera un par al arrancar (`generar-clave-si-falta`).
- **Refresh** de 7 días: se guarda solo el SHA-256, se rota en cada uso y reutilizar uno revocado cierra todas las sesiones del usuario.
  `POST /logout` y `POST /cambiar-clave` (mín. 8 caracteres con letras y números; cierra las demás sesiones).
- **Usuarios (solo ADMIN)** `/api/v1/auth/usuarios`: listar (texto, rol, activo), crear (sin clave → temporal que se muestra una vez y obliga a cambiarla),
  actualizar, roles, activar/desactivar, desbloquear, restablecer clave. No hay borrado físico (otros servicios guardan `usuario_id`): `DELETE` desactiva.
  Nunca se deja al sistema sin un ADMIN activo; cambiar roles o desactivar revoca las sesiones.
- Semillas DEV: `admin/admin123`, `secretaria/secre123`, `cajero/cajero123`.
- El Gateway enruta `/api/v1/auth/**`. Los tokens de los escenarios `tools/e2e/` ya salen de este servicio.
