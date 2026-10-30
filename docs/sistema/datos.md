# Datos y consistencia

## Principios
- **Una base por servicio** (PostgreSQL 16, Flyway, `ddl-auto: validate`). Ningún servicio lee la base de otro.
- Lo que otro servicio necesita llega por **evento** (réplica local) o por **consulta interna** a la fuente de verdad.
- El estado oficial de cada dato tiene un solo dueño (tabla siguiente).

| Dato | Dueño |
|---|---|
| Año, periodos, secciones, aulas, cursos, malla, docentes, asignaciones, horarios, feriados | planificacion |
| Alumno, apoderados, documentos, salud | alumnos |
| Relación alumno ↔ sección (matrícula), cupos | matricula |
| Dinero: tarifas, cuotas, pagos, comprobantes, caja | pago |
| Asistencia, justificaciones | asistencia |
| Tareas y entregas | tareas |
| Exámenes, intentos, respuestas | examenes |
| Notas oficiales, promedios, boletines | calificaciones |
| Avisos, plantillas, preferencias | notificaciones |
| Usuarios, roles, tokens de refresco | auth-ms |

## Tablas por servicio (bases y tamaño medido)

| Servicio | Base | Tablas (incl. técnicas) | Filas de la demo | Tamaño |
|---|---|---|---|---|
| auth-ms | `auth_db` | `usuarios`, `roles`, `usuario_roles`, `refresh_tokens` | 18 | 7,9 MB |
| planificacion-ms | `planificacion_db` | `anios_academicos`, `periodos`, `niveles`, `grados`, `secciones`, `aulas`, `cursos`, `grado_cursos`, `docentes`, `asignaciones`, `bloques_horarios`, `horarios`, `publicaciones_horario`, `feriados`, `outbox_eventos` | 563 | 9,1 MB |
| alumnos-ms | `alumnos_db` | `alumnos`, `apoderados`, `alumno_apoderado`, `alumno_documentos`, `alumno_salud`, `alumno_estado_historial`, `outbox_eventos` | 176 | 8,3 MB |
| matricula-ms | `matricula_db` | `campanias_matricula`, `matriculas`, `cupos_seccion`, `matricula_requisitos`, `matricula_historial`, `matriculas_publicas` (trámites en línea; estado INICIADA→PAGADA→APROBADA/RECHAZADA), `eventos_procesados`, `outbox_eventos`, `shedlock` | 88 | 8,2 MB |
| asistencia-ms | `asistencia_db` | `sesiones_asistencia`, `asistencia_detalle`, `justificaciones`, `asistencia_auditoria`, `matriculas_local`, `eventos_procesados`, `shedlock` | 37 | 8,1 MB |
| calificaciones-ms | `calificaciones_db` | `criterios_evaluacion`, `notas`, `notas_pendientes`, `promedios_periodo`, `cierres_periodo`, `boletines`, `resultados_anuales`, `escalas_literales`, `parametros_evaluacion`, `matriculas_local`, `eventos_procesados`, `shedlock` | 61 | 8,4 MB |
| pago-ms | `pago_db` | `conceptos_pago`, `tarifas`, `descuentos`, `descuentos_alumno`, `cuotas`, `pagos`, `pago_detalle`, `comprobantes`, `series_comprobante`, `cajas_sesion`, `alertas_admin`, `matriculas_local`, `eventos_procesados`, `outbox_eventos`, `shedlock` | 119 | 8,7 MB |
| examenes-ms | `examenes_db` | `preguntas`, `opciones_pregunta`, `examenes`, `examen_preguntas`, `intentos`, `respuestas`, `matriculas_local`, `eventos_procesados`, `shedlock` | 45 | 8,3 MB |
| tareas-ms | `tareas_db` | `tareas`, `tarea_adjuntos`, `materiales` (archivos y enlaces del curso), `entregas`, `entrega_archivos`, `matriculas_local`, `eventos_procesados`, `shedlock` | 26 | 8,2 MB |
| notificaciones-ms | `notificaciones_db` | `notificaciones`, `plantillas_notificacion`, `preferencias_notificacion`, `dispositivos_push`, `intentos_envio`, `eventos_procesados`, `shedlock` | 176 | 8,2 MB |

(Conteo de filas aproximado de `pg_stat_user_tables` el 2026-10-29; incluye las tablas de Flyway.)

## Mensajería
- **25 tópicos** y **18 colas de errores** (`<tópico>.DLT`).
- Sobre estándar plano: `eventId`, `eventType`, `occurredAt`, `version`, `traceId` + campos de negocio. Serialización de texto JSON (Jackson 3).
- Tabla de productores y consumidores: [equipo/README](../equipo/README.md).

## Consistencia y compensación
| Problema | Solución |
|---|---|
| Publicar el evento solo si la transacción se confirma | **Outbox**: `outbox_eventos` en la misma transacción; un relé cada ~2 s marca `enviado_en` |
| Procesar dos veces el mismo evento | `eventos_procesados` (por `eventId`) + restricciones únicas |
| Última vacante con dos matrículas a la vez | `UPDATE cupos_seccion SET ocupados = ocupados+1 WHERE ocupados < capacidad` |
| Cobro repetido | `idempotency_key` + `version` (optimista) |
| Numeración de comprobantes sin huecos | `series_comprobante` con bloqueo |
| Examen: un solo intento en curso y tiempo del servidor | UPSERT con índice único parcial + `vence_en` + job de cierre |
| Pago anulado o matrícula retirada | Compensación por eventos: cuotas anuladas, cupo liberado |
| Jobs duplicados con varias réplicas | ShedLock |
| Notas de tareas/exámenes sin criterio | `notas_pendientes` persistentes hasta asignarlas |

Estado medido: outbox sin eventos pendientes en planificacion, alumnos, matricula y pago (`enviado_en IS NULL` = 0) y **lag 0** en los 7 grupos de consumidores.

## Archivos
MinIO con URL firmada: documentos del expediente, archivos de justificaciones, adjuntos y entregas de tareas, boletines PDF. La base solo guarda la ruta.
