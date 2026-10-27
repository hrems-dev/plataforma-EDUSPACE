# Informe de desarrollo — Andre Fabricio
## `matricula-ms` · `asistencia-ms` · `tareas-ms`

**Proyecto:** EduSpace · **Base:** pagatu · **Documentos de apoyo:** `00-general` (mapa, estándares, huecos) y `01-infraestructura-inicial` (§9 plantilla común de cada servicio).
**Convención:** "debe hacer" = comportamiento del código; no se incluye código. Los archivos que son idénticos en todos los servicios (pom base, Dockerfile, compose, logback, `CorrelationIdFilter`, `GlobalExceptionHandler`…) se describen en la **plantilla común** (infraestructura §9); aquí solo se detalla lo propio de cada servicio.

| Servicio | Puerto app | Base de datos | Puerto BD DEV/PROD | Tablas | Script |
|---|---|---|---|---|---|
| `matricula-ms` | 8093 | `matricula_db` | 15443 / 25443 | 8 | `sql/matricula-ms/V1__init_matricula.sql` |
| `asistencia-ms` | 8094 | `asistencia_db` | 15444 / 25444 | 7 | `sql/asistencia-ms/V1__init_asistencia.sql` |
| `tareas-ms` | 8098 | `tareas_db` | 15448 / 25448 | 7 | `sql/tareas-ms/V1__init_tareas.sql` |

Orden recomendado de trabajo: **matrícula → asistencia → tareas** (los otros dos dependen de los eventos de matrícula).

---

# PARTE 1 — `matricula-ms`

## 1.1 Qué hace
Registra la matrícula de un alumno en un año, grado y sección; controla los cupos sin sobrepasar la capacidad; maneja pre-matrícula, confirmación al pagar, traslado de sección, retiro y ratificación del año siguiente. Es el **dueño de la relación alumno ↔ sección**: todos los demás servicios dependen de sus eventos.

## 1.2 Qué NO hace
No guarda datos personales del alumno (los pide a `alumnos-ms`), ni define secciones ni capacidades (las pide a `planificacion-ms`), ni cobra (lo hace `pago-ms`).

## 1.3 Base de datos `matricula_db` (8 tablas)
| Tabla | Para qué |
|---|---|
| `campanias_matricula` | Periodos de matrícula por año (planificada/abierta/cerrada). Solo se matricula si hay campaña ABIERTA. |
| `cupos_seccion` | Capacidad y ocupados por sección. **Control de cupo atómico.** |
| `matriculas` | La matrícula: alumno, año, grado, sección, tipo, estado, vencimiento de pre-matrícula, versión (bloqueo optimista). |
| `matricula_requisitos` | Checklist (ficha firmada, constancia de no adeudo, certificado, expediente completo). |
| `matricula_historial` | Todo cambio de estado o de sección, con motivo y usuario. |
| `outbox_eventos` | Eventos pendientes de enviar a Kafka (garantiza no perderlos). |
| `eventos_procesados` | Idempotencia de los eventos que consume. |
| `shedlock` | Un solo job activo entre réplicas. |

Reglas que ya impone la BD: **una sola matrícula viva (PRE_MATRICULA o MATRICULADO) por alumno y año**; `ocupados` entre 0 y `capacidad`; código de matrícula único; estados y tipos restringidos.

## 1.4 Estados de la matrícula
`PRE_MATRICULA` → (pago de matrícula) → `MATRICULADO` → `RETIRADO` / `TRASLADADO`. Desde `PRE_MATRICULA` también → `ANULADO` (por vencimiento o decisión de secretaría). `TRASLADADO` se usa cuando el alumno sale del colegio; el cambio de sección **no** cambia de estado (queda `MATRICULADO` con otra sección y una fila `TRASLADO_SECCION` en el historial).

## 1.5 Archivos específicos

### `config/`
| Clase | Debe hacer |
|---|---|
| `SecurityConfig` | Lectura: ADMIN, SECRETARIA, DOCENTE; escritura: ADMIN, SECRETARIA; ALUMNO/APODERADO solo ven sus matrículas (filtro fino en servicio vía `alumnos-ms` con caché); `/internal/**` permitido. |
| `FeignConfig` | Habilita clientes Feign; propaga `X-Trace-ID`. |
| `Resilience4jConfig` / YAML | Instancias `alumnosClient` y `planificacionClient` (ventana 5, 50 %, 10 s abierto, 3 en medio-abierto). |
| `CacheConfig` | Caché Caffeine: sección (5 min), año activo (5 min). |
| `KafkaProducerConfig`, `KafkaConsumerConfig`, `KafkaTopicsConfig` | Productor JSON sin cabeceras de tipo; consumidor con `ErrorHandlingDeserializer`, `group-id=matricula-ms`; tópicos propios: `matricula.creada`, `.confirmada`, `.trasladada`, `.retirada`, `.anulada`. |
| `ShedLockConfig`, `SchedulingConfig` | Habilitan tareas programadas con bloqueo. |

### `client/`
| Clase | Debe hacer |
|---|---|
| `AlumnosClient` | `GET /internal/alumnos/{id}` (estado), `GET /internal/alumnos/{id}/expediente-completo`, `GET /internal/alumnos/{id}/responsable-pago`. |
| `PlanificacionClient` | `GET /internal/secciones/{id}`, `GET /internal/anios/activo`, `GET /internal/grados/{id}`. |
| `AlumnosGateway`, `PlanificacionGateway` | Beans **aparte** con `@CircuitBreaker` y fallback (503 con mensaje claro: no se puede matricular sin validar). |

### `entity/`
`CampaniaMatricula`, `CupoSeccion`, `Matricula` (con `@Version`), `MatriculaRequisito`, `MatriculaHistorial`, `OutboxEvento`, `EventoProcesado`. Reflejan el SQL exactamente.

### `repository/`
- `CupoSeccionRepository`: **método de reserva** `ocupados+1 si ocupados<capacidad` (devuelve filas afectadas; 0 = sin cupo) y de liberación (`ocupados-1` si > 0).
- `MatriculaRepository`: por alumno+año vivo, por sección y estado, por año y estado (paginado con filtros), vencidas (`vence_en < ahora` y PRE_MATRICULA), conteo por sección.
- `OutboxRepository`: tomar lote pendiente con bloqueo `SKIP LOCKED`; marcar enviado.
- `EventoProcesadoRepository`: existe/insertar.

### `dto/` y `mapper/`
`MatriculaCreateRequest` (alumnoId, seccionId, tipo, observaciones), `MatriculaResponse`, `MatriculaResumenResponse`, `TrasladoRequest` (nuevaSeccionId, motivo), `RetiroRequest` (motivo), `RatificacionRequest` (anioDestinoId, opcional seccionId), `RequisitoRequest/Response`, `CupoResponse`, `CampaniaRequest/Response`, `HistorialResponse`, `RatificacionMasivaResultado` (cuántas creadas, cuáles con error y por qué). MapStruct para todo.

### `service/`
| Servicio | Debe hacer |
|---|---|
| `CampaniaService` | CRUD de campañas; abrir/cerrar; una ABIERTA por año. |
| `CupoService` | Crear/sincronizar `cupos_seccion` desde la sección de planificación la primera vez que se usa; **reservar** y **liberar** cupo con las consultas atómicas; ofrecer vacantes por grado. |
| `MatriculaService` (creación) | Validaciones en este orden: campaña ABIERTA → alumno existe y ACTIVO (Feign) → no tiene matrícula viva en el año → expediente completo (Feign; si falta, rechaza con la lista de faltantes) → sección pertenece al grado y año → **reservar cupo**. Guarda en PRE_MATRICULA con `vence_en` (parámetro, p. ej. 7 días), código correlativo, historial CREACION, checklist inicial, y escribe `matricula.creada` en el **outbox** — todo en **una** transacción. |
| `MatriculaService` (confirmar) | Disparado por el consumidor de `pago.confirmado`; solo si está PRE_MATRICULA; pasa a MATRICULADO, registra `confirmada_en` e historial, escribe `matricula.confirmada`. Si ya estaba confirmada, no hace nada (idempotente). |
| `MatriculaService` (traslado de sección) | Dentro del mismo año y grado: reserva en la nueva sección, libera la anterior, historial, evento `matricula.trasladada`. Si la nueva no tiene cupo, no se toca nada. |
| `MatriculaService` (retiro / anulación) | Retiro: solo MATRICULADO, motivo obligatorio; anulación: solo PRE_MATRICULA. Ambos **liberan cupo**, escriben historial y evento (`matricula.retirada` / `matricula.anulada`). |
| `RatificacionService` | Para el año siguiente: dado un alumno (o toda una sección), crea matrícula tipo RATIFICACION enlazada a la anterior (`matricula_anterior_id`), validando que el alumno aprobó (consulta futura a calificaciones; en MVP lo decide secretaría) y que **no tiene deuda** (la deuda llega por evento/consulta de pago). Las que fallan se reportan sin detener las demás. |
| `RequisitoService` | Marcar requisitos cumplidos con usuario y fecha. |
| `VencimientoJob` (con ShedLock) | Cada hora: anula las PRE_MATRICULA vencidas (libera cupo, historial, evento `matricula.anulada`). |
| `OutboxPublisher` (con ShedLock no necesario por `SKIP LOCKED`) | Cada 1–2 s toma un lote pendiente, lo envía a Kafka con clave = `matriculaId`, marca enviado o suma intento y guarda el error. Alerta por log si un evento supera N intentos. |
| `LimpiezaJob` | Diario: borra `outbox_eventos` enviados y `eventos_procesados` de más de 30 días. |

### `messaging/` y `event/`
| Clase | Debe hacer |
|---|---|
| `event/MatriculaEvent` (y variantes) | Sobre estándar + matriculaId, alumnoId, alumnoNombre, anioId, nivelId, gradoId, seccionId, estado, tipo, seccionAnteriorId / motivo según el caso, `responsablePagoUsuarioId` y `responsablePagoNombre`. |
| `event/PagoConfirmadoEvent` | Copia propia del contrato: matriculaId, conceptoTipo, pagoId, monto. |
| `messaging/PagoEventConsumer` | Escucha `pago.confirmado`: si `eventId` ya procesado → ignora; si `conceptoTipo != MATRICULA` → ignora; si no, llama a confirmar. Marca el evento como procesado en la misma transacción. Si la matrícula no existe (llegó antes de ser visible) → reintento acotado y luego DLT. |
| `messaging/PagoAnuladoConsumer` | Escucha `pago.anulado` de concepto MATRICULA: no revierte solo; deja un registro en historial y notifica a secretaría (decisión manual). |

### `controller/` — API `/api/v1/matriculas`
| Endpoint | Función | Roles |
|---|---|---|
| `GET /` | Listar con filtros (anio, grado, seccion, estado, texto) paginado | ADMIN, SECRETARIA, DOCENTE |
| `GET /{id}` | Detalle con historial y requisitos | ídem + dueño |
| `POST /` | Crear matrícula | ADMIN, SECRETARIA |
| `PATCH /{id}/traslado` | Cambiar de sección | ADMIN, SECRETARIA |
| `PATCH /{id}/retiro` | Retirar | ADMIN, SECRETARIA |
| `PATCH /{id}/anular` | Anular pre-matrícula | ADMIN, SECRETARIA |
| `PUT /{id}/requisitos/{tipo}` | Marcar requisito | ADMIN, SECRETARIA |
| `POST /ratificacion` | Ratificación individual o masiva | ADMIN, SECRETARIA |
| `GET /cupos?anioId=&gradoId=` | Vacantes disponibles por sección | ADMIN, SECRETARIA |
| `GET/POST/PUT /campanias` | Gestionar campañas | ADMIN |
| `GET /mias` | Matrículas de los hijos del usuario | APODERADO, ALUMNO |
| `GET /seccion/{seccionId}/alumnos` | Lista de la sección (nombre, matrícula, estado) | ADMIN, SECRETARIA, DOCENTE |
| `/internal/matriculas/seccion/{id}` | Lista de matrículas vivas (carga inicial de réplicas) | sin Gateway |
| `/internal/matriculas/alumno/{alumnoId}?anioId=` | Matrícula vigente del alumno | sin Gateway |

### Pruebas clave
- Dos solicitudes simultáneas por el **último cupo**: una gana, la otra recibe 409 (prueba concurrente).
- Mismo alumno dos veces en el mismo año → 409.
- Pre-matrícula vencida se anula y libera el cupo.
- `pago.confirmado` repetido no confirma dos veces.
- Evento guardado en outbox aunque Kafka esté caído, y enviado al volver.
- Traslado a sección llena no cambia nada.

## 1.6 Huecos que esta parte cubre
H1 (réplicas de matrícula para los demás), H4 (vencimiento + flujo de pago), H7 (ratificación), C1 (outbox), C2 (idempotencia), C6 (último cupo).

---

# PARTE 2 — `asistencia-ms`

## 2.1 Qué hace
Registra la asistencia de los alumnos por día (primaria) o por curso y bloque (secundaria), maneja tardanzas, justificaciones de los apoderados, auditoría de correcciones y reportes. Avisa al apoderado cuando hay falta.

## 2.2 Qué NO hace
No sabe quién pertenece a una sección (usa su **réplica `matriculas_local`**) ni quién enseña qué (lo valida con `planificacion-ms`).

## 2.3 Base de datos `asistencia_db` (7 tablas)
| Tabla | Para qué |
|---|---|
| `matriculas_local` | Réplica de matrículas por eventos (alumno, nombre, sección, estado). |
| `sesiones_asistencia` | Una "hoja de asistencia": sección + fecha (+ curso y bloque si es por curso); estado ABIERTA/CERRADA. |
| `asistencia_detalle` | Estado de cada alumno en la sesión: PRESENTE, FALTA, TARDANZA (minutos), JUSTIFICADO. |
| `justificaciones` | Solicitud del apoderado con rango de fechas, motivo, archivo y resolución. |
| `asistencia_auditoria` | Cambios hechos sobre una asistencia ya registrada. |
| `eventos_procesados`, `shedlock` | Idempotencia y jobs. |

Reglas de la BD: **una sola sesión DIA por sección y fecha**; una sola sesión CURSO por sección/fecha/curso/bloque; coherencia tipo ↔ curso/bloque; un detalle por alumno y sesión.

## 2.4 Archivos específicos

### `config/`
| Clase | Debe hacer |
|---|---|
| `SecurityConfig` | Docente: solo en sus secciones (validación fina en servicio); SECRETARIA/ADMIN: lectura y justificaciones; APODERADO/ALUMNO: solo propio (vía caché de hijos); `/internal/**` permitido. |
| `FeignConfig`, `Resilience4jConfig`, `CacheConfig` | Clientes a `planificacion-ms` y `alumnos-ms`; breakers; caché de asignaciones del docente (60 s), feriados (24 h) y de hijos del apoderado (5 min). |
| `KafkaProducerConfig`, `KafkaConsumerConfig`, `KafkaTopicsConfig` | Produce `asistencia.falta-registrada` (y opcional `justificacion.resuelta`); consume `matricula.*` con `group-id=asistencia-ms`. |
| `StorageConfig` | Cliente MinIO, bucket `justificaciones` (URL firmadas de subida/descarga). |
| `ShedLockConfig`, `SchedulingConfig` | Jobs con bloqueo. |

### `client/`
`PlanificacionClient` (asignaciones por usuario, validar asignación, `es-habil`, sección) y `AlumnosClient` (hijos del apoderado, alumno por usuario) con sus gateways con circuit breaker. **Fallback de autorización = denegar.**

### `entity/` y `repository/`
Entidades: `MatriculaLocal`, `SesionAsistencia`, `AsistenciaDetalle`, `Justificacion`, `AsistenciaAuditoria`, `EventoProcesado`.
Consultas: alumnos activos de una sección (de `matriculas_local`); sesión por sección+fecha(+curso+bloque); detalle por alumno en rango de fechas; conteo de faltas por alumno/periodo; sesiones ABIERTAS de fechas pasadas; justificaciones pendientes; faltas en rango de una matrícula (para aplicar justificación).

### `dto/` y `mapper/`
`SesionCreateRequest` (seccionId, fecha, tipo, cursoId?, bloqueId?), `SesionResponse` (con lista de alumnos y estado), `RegistroAsistenciaRequest` (lista de `{matriculaId, estado, minutosTarde?, observacion?}` — **registro masivo de toda la sección en una sola petición**), `CorreccionRequest` (nuevo estado + motivo), `JustificacionRequest/Response`, `ResolverJustificacionRequest`, `ResumenAsistenciaResponse` (días, presentes, faltas, tardanzas, justificadas, % por alumno), `ReporteSeccionResponse`.

### `service/`
| Servicio | Debe hacer |
|---|---|
| `MatriculaLocalService` | Procesa eventos `matricula.*`: crea o actualiza la réplica (UPSERT por `matriculaId`); ignora eventos viejos si ya procesó uno más reciente; guarda `eventId` en `eventos_procesados`. Carga inicial desde `/internal/matriculas/seccion/{id}` si la réplica está vacía. |
| `SesionService` | Abrir sesión: valida que el docente esté asignado a esa sección (y curso, si es por curso), que la fecha sea hábil (no feriado ni fin de semana) y que no sea futura; precarga detalles con todos los alumnos activos de la sección en estado PRESENTE por defecto (configurable). |
| `RegistroAsistenciaService` | Guarda el lote de asistencia en **una transacción**; valida que cada matrícula pertenece a la sección; tardanza exige minutos; por cada FALTA nueva escribe la notificación (publica `asistencia.falta-registrada`) **solo una vez** por alumno/día aunque se vuelva a guardar. Si la sesión está CERRADA, rechaza (409). |
| `CorreccionService` | Permite corregir una sesión cerrada solo a ADMIN/SECRETARIA con motivo; deja fila en `asistencia_auditoria`. |
| `CierreSesionService` | Cierre manual por el docente; **job nocturno** (ShedLock) cierra las sesiones abiertas de días anteriores y marca como FALTA a quienes quedaron sin registrar si la política lo indica. |
| `JustificacionService` | Apoderado crea solicitud (rango de fechas, motivo, archivo); secretaría aprueba/rechaza; al **aprobar**, convierte las FALTAS del rango en JUSTIFICADO, con auditoría. |
| `ReporteService` | Resúmenes por alumno, por sección y por periodo; alumnos con exceso de faltas (alerta); exportación para el escritorio. |

### `messaging/` y `event/`
- `MatriculaEventConsumer`: escucha `matricula.creada`, `.confirmada`, `.trasladada`, `.retirada`, `.anulada` y delega en `MatriculaLocalService`. Un traslado actualiza solo la sección (la asistencia pasada se queda con la sección donde ocurrió, por `sesion.seccion_id`).
- `AsistenciaEventProducer`: publica `asistencia.falta-registrada` con clave = id del detalle, después del commit.
- Eventos: `AsistenciaFaltaRegistradaEvent` (productor), `MatriculaEvent` (consumidor, copia propia).

### `controller/` — API `/api/v1/asistencia`
| Endpoint | Función | Roles |
|---|---|---|
| `POST /sesiones` | Abrir sesión | DOCENTE (asignado), ADMIN |
| `GET /sesiones?seccionId=&fecha=` | Obtener hoja con alumnos | DOCENTE, SECRETARIA, ADMIN |
| `PUT /sesiones/{id}/registro` | Guardar asistencia de toda la sección | DOCENTE, ADMIN |
| `PATCH /sesiones/{id}/cerrar` | Cerrar | DOCENTE, ADMIN |
| `PATCH /detalle/{id}` | Corregir con motivo | ADMIN, SECRETARIA |
| `GET /alumnos/{alumnoId}/resumen?periodoId=` | Resumen del alumno | dueño, SECRETARIA, DOCENTE, ADMIN |
| `GET /secciones/{id}/reporte?desde=&hasta=` | Reporte de sección | SECRETARIA, DOCENTE, ADMIN |
| `POST /justificaciones` | Solicitar | APODERADO |
| `GET /justificaciones?estado=` | Bandeja | SECRETARIA, ADMIN |
| `PATCH /justificaciones/{id}/resolver` | Aprobar/rechazar | SECRETARIA, ADMIN |
| `POST /justificaciones/{id}/archivo-url` | URL firmada de subida | APODERADO |
| `GET /mias` | Asistencia de los hijos | APODERADO, ALUMNO |

### Pruebas clave
- Docente no asignado → 403; fecha feriado → 409.
- Guardar dos veces la misma hoja no duplica faltas ni notificaciones.
- Aprobar justificación convierte exactamente las faltas del rango.
- `matricula.trasladada` actualiza la lista del día siguiente, no la historia.
- Con `planificacion-ms` caído se bloquea registrar pero se pueden consultar reportes.

## 2.5 Huecos que cubre
H1, H2, H11, H12, C2, C4.

---

# PARTE 3 — `tareas-ms`

## 3.1 Qué hace
El docente publica tareas por curso y sección (con adjuntos); el alumno entrega archivos y comentarios; el docente califica con retroalimentación; la nota viaja a `calificaciones-ms` y el apoderado/alumno recibe avisos.

## 3.2 Qué NO hace
No decide la nota oficial del periodo (eso es `calificaciones-ms`), no guarda archivos (MinIO) ni sabe qué alumnos tiene la sección (réplica `matriculas_local`).

## 3.3 Base de datos `tareas_db` (7 tablas)
| Tabla | Para qué |
|---|---|
| `matriculas_local` | Réplica por eventos. |
| `tareas` | Tarea: sección, curso, periodo, docente, fechas, puntaje máximo, estado BORRADOR/PUBLICADA/CERRADA. |
| `tarea_adjuntos` | Material del docente (ruta en MinIO). |
| `entregas` | Una entrega por alumno y tarea (única), con versión, tardía, nota y retroalimentación. |
| `entrega_archivos` | Archivos de cada entrega. |
| `eventos_procesados`, `shedlock` | Idempotencia y jobs. |

## 3.4 Archivos específicos

### `config/`
| Clase | Debe hacer |
|---|---|
| `SecurityConfig` | DOCENTE: crea/califica solo en sus secciones y cursos; ALUMNO: entrega solo la suya; APODERADO: solo lectura de sus hijos; ADMIN todo. |
| `StorageConfig` | MinIO, bucket `tareas-entregas`; tipos de archivo permitidos y tamaño máximo por archivo y por entrega (configurables). |
| `FeignConfig`, `Resilience4jConfig`, `CacheConfig` | Cliente a `planificacion-ms` (asignaciones, periodo) y `alumnos-ms` (hijos del apoderado). |
| `Kafka*Config` | Produce `tarea.publicada`, `tarea.entregada`, `tarea.calificada`; consume `matricula.*` (`group-id=tareas-ms`). |
| `ShedLockConfig`, `SchedulingConfig` | Jobs. |

### `entity/` y `repository/`
`MatriculaLocal`, `Tarea`, `TareaAdjunto`, `Entrega`, `EntregaArchivo`, `EventoProcesado`.
Consultas: tareas por sección/estado/rango de fechas; tareas pendientes de un alumno (publicadas sin entrega); entregas por tarea (con estado); entregas sin calificar; tareas que vencen en las próximas 24 h.

### `dto/` y `mapper/`
`TareaCreateRequest/UpdateRequest`, `TareaResponse` (con adjuntos y contadores entregadas/calificadas/pendientes), `EntregaRequest` (comentario + lista de archivos ya subidos), `EntregaResponse`, `CalificarRequest` (nota, retroalimentación), `CalificacionMasivaRequest`, `ArchivoUrlRequest/Response` (URL firmada), `TareaPendienteResponse`.

### `service/`
| Servicio | Debe hacer |
|---|---|
| `MatriculaLocalService` | Igual que en asistencia (UPSERT por eventos, idempotente). |
| `TareaService` | Crear en BORRADOR; validar que el docente esté asignado a la sección y curso (Feign + caché) y que el periodo exista y esté activo; **publicar**: exige fecha de entrega futura, cambia a PUBLICADA y emite `tarea.publicada` con la lista de `alumnoIds` de la sección (de la réplica). Editar tarea publicada permitido solo en descripción y fecha (nunca puntaje si ya hay notas). Cerrar. |
| `EntregaService` | Alumno entrega: valida que su matrícula pertenece a la sección, que la tarea está PUBLICADA y que no venció (o que permite tardía → marca `es_tardia`); puede **reenviar** (sube `version`) mientras no esté CALIFICADA; guarda archivos (metadatos) y publica `tarea.entregada`. |
| `ArchivoService` | Genera URL firmada de subida (clave con id de tarea/alumno, nombre saneado); valida tipo y tamaño antes de firmar; URL firmada de descarga para docente/alumno propietario. |
| `CalificacionService` | Docente pone nota y retroalimentación (entre 0 y puntaje máximo); pasa a CALIFICADA; **convierte la nota a escala 0–20** y publica `tarea.calificada` con `tareaId, entregaId, alumnoId, matriculaId, seccionId, cursoId, periodoId, nota`. Si recalifica, vuelve a publicar con la nueva nota (calificaciones la actualiza por `origen_id`). Opción de devolver (DEVUELTA) para corregir. |
| `PendientesService` | Lista de tareas pendientes del alumno y del apoderado (por hijo). |
| `RecordatorioJob` (ShedLock) | Cada hora: para tareas que vencen en <24 h, avisa a los alumnos sin entrega (publica evento o llama a notificaciones según se acuerde) — una sola vez por tarea. |
| `CierreAutomaticoJob` | Cierra tareas vencidas que no permiten entrega tardía. |

### `messaging/` y `event/`
- `MatriculaEventConsumer` (réplica), `TareaEventProducer` (3 eventos, clave = id de tarea o entrega).
- Eventos: `TareaPublicadaEvent`, `TareaEntregadaEvent`, `TareaCalificadaEvent`.

### `controller/` — API `/api/v1/tareas`
| Endpoint | Función | Roles |
|---|---|---|
| `POST /` · `PUT /{id}` · `PATCH /{id}/publicar` · `PATCH /{id}/cerrar` | Gestión de tareas | DOCENTE asignado, ADMIN |
| `GET /` | Listar (filtros sección/curso/estado/periodo) | DOCENTE, SECRETARIA, ADMIN |
| `GET /{id}` | Detalle con adjuntos | participantes |
| `POST /{id}/adjuntos/url` | URL firmada de subida de material | DOCENTE |
| `POST /{id}/entregas/archivo-url` | URL firmada de subida de entrega | ALUMNO |
| `POST /{id}/entregas` | Registrar entrega | ALUMNO |
| `GET /{id}/entregas` | Entregas con estado | DOCENTE, ADMIN |
| `PATCH /entregas/{id}/calificar` · `…/devolver` | Calificar / devolver | DOCENTE |
| `GET /entregas/{id}/archivos/{archivoId}/url` | Descargar | docente, dueño |
| `GET /mias/pendientes` · `GET /mias` | Tareas del alumno o de los hijos | ALUMNO, APODERADO |

### Pruebas clave
- Alumno de otra sección no puede entregar.
- Entrega fuera de plazo con y sin `permite_tardia`.
- Reenvío incrementa versión; después de calificar ya no se puede.
- `tarea.calificada` lleva nota en escala 0–20 y `periodoId`.
- La subida directa con URL firmada rechaza tipos/tamaños no permitidos.
- El recordatorio no se duplica con dos réplicas.

## 3.5 Huecos que cubre
H1, H2, H6, C4, C8.

---

## 4. Eventos de Andre (resumen)

| Produce | Consume |
|---|---|
| `matricula.creada/.confirmada/.trasladada/.retirada/.anulada` | `pago.confirmado`, `pago.anulado` (matricula) |
| `asistencia.falta-registrada` | `matricula.*` (asistencia y tareas) |
| `tarea.publicada`, `tarea.entregada`, `tarea.calificada` | — |

Contratos y sobre estándar: informe general §5.

## 5. Dependencias con los otros dos integrantes

| Con quién | Qué se acuerda |
|---|---|
| **Hermes** | Endpoints internos de planificación y alumnos (adenda A2 y A3). Sin ellos no se puede validar docente/apoderado ni sección. |
| **Witmer** | `pago.confirmado` con `conceptoTipo` y `matriculaId`; `matricula.creada` con el responsable de pago; `tarea.calificada` y `examen.calificado` con `periodoId` y nota 0–20 para `calificaciones-ms`. |

## 6. Lista de verificación de Andre
1. `matricula_db` migra sin error y la app arranca con `validate`.
2. Crear matrícula → aparece `matricula.creada` en Kafka UI (vía outbox).
3. Simular `pago.confirmado` → matrícula pasa a MATRICULADO y sale `matricula.confirmada`.
4. En `asistencia-ms` y `tareas-ms`, tras el evento, aparece el alumno en `matriculas_local`.
5. Docente pasa lista de una sección completa y el apoderado recibe la notificación de falta.
6. Tarea publicada → entrega → calificación → `tarea.calificada` visible en Kafka UI con `periodoId`.
7. Prueba de concurrencia del último cupo.
8. Con dos réplicas de cada servicio los jobs no se duplican.
