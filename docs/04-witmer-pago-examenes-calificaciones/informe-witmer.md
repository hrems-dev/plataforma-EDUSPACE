# Informe de desarrollo — Witmer
## `pago-ms` · `examenes-ms` · `calificaciones-ms`

**Proyecto:** EduSpace · **Base:** pagatu · **Documentos de apoyo:** `00-general` (mapa, estándares, huecos) y `01-infraestructura-inicial` (§9 plantilla común).
**Convención:** "debe hacer" = comportamiento del código; no se incluye código. Lo idéntico en todos los servicios está en la plantilla común (infraestructura §9); aquí solo lo propio.

| Servicio | Puerto app | Base de datos | Puerto BD DEV/PROD | Tablas | Script |
|---|---|---|---|---|---|
| `pago-ms` | 8096 | `pago_db` | 15446 / 25446 | 14 | `sql/pago-ms/V1__init_pago.sql` |
| `examenes-ms` | 8097 | `examenes_db` | 15447 / 25447 | 9 | `sql/examenes-ms/V1__init_examenes.sql` |
| `calificaciones-ms` | 8095 | `calificaciones_db` | 15445 / 25445 | 11 | `sql/calificaciones-ms/V1__init_calificaciones.sql` |

Orden recomendado: **pago → examenes → calificaciones** (calificaciones consume lo que producen tareas y exámenes; pago es el que cierra el flujo de matrícula con Andre).

---

# PARTE 1 — `pago-ms`

## 1.1 Qué hace
Es el **dueño de todo el dinero**: tarifas, conceptos, descuentos, cuotas por alumno, registro de pagos (caja o en línea simulado), comprobantes, apertura y cierre de caja, mora y avisos de vencimiento. Cierra el flujo de matrícula: al cobrar la matrícula publica `pago.confirmado`.

## 1.2 Qué NO hace
No decide quién está matriculado (usa su réplica `matriculas_local`), no guarda datos del apoderado más allá del responsable de pago, ni emite facturas electrónicas SUNAT (fuera del MVP; los comprobantes son internos).

## 1.3 Base de datos `pago_db` (14 tablas)
| Tabla | Para qué |
|---|---|
| `matriculas_local` | Réplica de matrículas + responsable de pago (para listados y morosos). |
| `conceptos_pago` | MATRICULA, PENSION, CUOTA_APAFA, MATERIAL… con tipo (MATRICULA/PENSION/OTRO). |
| `tarifas` | Monto por año + nivel (+ grado opcional) + concepto, número de cuotas, mes de inicio y día de vencimiento. |
| `descuentos`, `descuentos_alumno` | Catálogo (porcentaje o monto fijo) y su asignación aprobada a un alumno/año. |
| `cuotas` | Lo que debe cada matrícula: base, descuento, mora, total, saldo, vencimiento, estado; `version` para bloqueo optimista. |
| `cajas_sesion` | Apertura y cierre de caja por cajero. |
| `series_comprobante` | Correlativo de RECIBO/BOLETA por serie. |
| `pagos`, `pago_detalle` | Cabecera del pago (medio, caja, cajero, anulación, `idempotency_key`) y su reparto entre cuotas. |
| `comprobantes` | Un comprobante por pago, con serie y número únicos. |
| `outbox_eventos`, `eventos_procesados`, `shedlock` | Entrega garantizada de eventos, idempotencia y jobs. |

Reglas ya impuestas por la BD: **no se duplican cuotas** si un evento se repite (`uk_cuota`); saldo entre 0 y el total; **una caja abierta por cajero**; número de comprobante único; **una clave de idempotencia por pago**; tarifa única por año/nivel/grado/concepto; descuentos válidos (porcentaje ≤ 100).

## 1.4 Estados de la cuota
`PENDIENTE` → `PARCIAL` (pago incompleto) → `PAGADA`. Desde PENDIENTE/PARCIAL → `VENCIDA` (job diario, con mora). `ANULADA` si la matrícula se anula o retira antes de pagar.

## 1.5 Archivos específicos

### `config/`
| Clase | Debe hacer |
|---|---|
| `SecurityConfig` | CAJERO: caja, cobros, comprobantes; ADMIN: tarifas, descuentos, anulaciones; SECRETARIA: lectura; APODERADO: ver y pagar cuotas de sus hijos (vía caché de hijos); `/internal/**` permitido. |
| `FeignConfig`, `Resilience4jConfig`, `CacheConfig` | Cliente a `alumnos-ms` (responsable de pago, hijos del apoderado) con breaker y caché. |
| `Kafka*Config` | Produce `pago.confirmado`, `pago.por-vencer`, `pago.vencido`, `pago.anulado`; consume `matricula.*` (`group-id=pago-ms`). |
| `StorageConfig` | MinIO, bucket `comprobantes` (PDF del comprobante). |
| `ShedLockConfig`, `SchedulingConfig` | Jobs con bloqueo. |
| `PagoProperties` | Parámetros: porcentaje de mora y su base de cálculo, días de aviso previo, vigencia de pre-matrícula, política de redondeo. |

### `client/`
`AlumnosClient`: `responsable-pago`, hijos del apoderado por usuario. Gateway con breaker; si el responsable no responde al crear la cuota, la cuota se crea **sin responsable** y un job la completa después (la cuota nunca se pierde).

### `entity/` y `repository/`
Entidades: `MatriculaLocal`, `ConceptoPago`, `Tarifa`, `Descuento`, `DescuentoAlumno`, `Cuota` (con `@Version`), `CajaSesion`, `SerieComprobante`, `Pago`, `PagoDetalle`, `Comprobante`, `OutboxEvento`, `EventoProcesado`.
Consultas: cuotas por alumno/estado; pendientes de un alumno ordenadas por vencimiento; cuotas que vencen en N días y sin aviso; cuotas vencidas sin marcar; morosos por sección/grado (suma de saldos); tarifa vigente por año/nivel/grado/concepto (con regla "grado específico gana sobre nivel"); caja abierta del cajero; **serie con bloqueo `FOR UPDATE`**; pago por `idempotency_key`; total cobrado por caja (para el cierre).

### `dto/` y `mapper/`
`ConceptoRequest/Response`, `TarifaRequest/Response`, `DescuentoRequest/Response`, `AsignarDescuentoRequest`, `CuotaResponse` (con estado y días de atraso), `EstadoCuentaResponse` (todas las cuotas de un alumno con totales), `RegistrarPagoRequest` (alumnoId, lista de `{cuotaId, monto}`, medio, referencia, `idempotencyKey`), `PagoResponse`, `ComprobanteResponse`, `AbrirCajaRequest`, `CerrarCajaRequest` (monto declarado), `CierreCajaResponse` (totales por medio y diferencia), `AnularPagoRequest` (motivo), `MorososResponse`, `ReporteCobranzaResponse`.

### `service/`
| Servicio | Debe hacer |
|---|---|
| `MatriculaLocalService` | Procesa `matricula.*` con UPSERT por `matriculaId`; guarda responsable de pago si viene en el evento; idempotente por `eventId`. |
| `ConceptoService`, `TarifaService`, `DescuentoService` | CRUD (ADMIN). Una tarifa no se modifica si ya generó cuotas: se crea otra para el siguiente año o se desactiva. |
| `GeneradorCuotasService` | **Al `matricula.creada`:** crea la cuota de MATRICULA (tarifa de su nivel/grado, descuento si aplica). **Al `matricula.confirmada`:** genera las cuotas de PENSION (número, mes de inicio y día de vencimiento de la tarifa; descuento aplicado por cuota; monto redondeado con la diferencia en la última cuota). **Al `matricula.retirada`/`anulada`:** anula las cuotas sin pago; las pagadas se conservan; las parciales quedan para decisión de administración. Cada cuota se crea con la restricción `uk_cuota` (un evento repetido no duplica). Si no existe tarifa → registra error visible y no genera (alerta a administración). |
| `PagoService` (registrar) | En **una transacción**: valida idempotencia (si ya existe la `idempotencyKey` devuelve el pago original); valida caja abierta si el medio es presencial; para cada cuota verifica estado y que el monto ≤ saldo; **bloqueo optimista**: si dos cajeros cobran la misma cuota, el segundo recibe 409; actualiza saldos y estados; guarda pago + detalle; emite comprobante; escribe en outbox `pago.confirmado` (con `conceptoTipo`, `matriculaId`, `cuotaIds`). Pago parcial → cuota PARCIAL. |
| `ComprobanteService` | Toma el siguiente número de la serie con `FOR UPDATE`, crea el comprobante, genera PDF (plantilla simple) y lo guarda en MinIO; permite reimpresión (escritorio). |
| `AnulacionService` | ADMIN anula un pago con motivo: restaura saldos y estados, anula comprobante (sin reutilizar el número), escribe `pago.anulado`. No permite anular pagos de una caja ya cerrada sin permiso explícito. |
| `CajaService` | Abrir (con monto inicial; falla si ya hay una abierta); cerrar (declara efectivo; el sistema calcula el efectivo esperado = apertura + cobros en efectivo; guarda la diferencia); reporte del día por medio de pago. |
| `MoraService` | Calcula mora de una cuota vencida según parámetros (regla simple y documentada: porcentaje sobre el saldo por periodo de atraso, con tope); la mora aumenta el total y el saldo; nunca modifica cuotas ya pagadas. |
| `VencimientoJob` (ShedLock, diario 00:30 hora de Lima) | Marca cuotas vencidas, calcula mora, publica `pago.vencido` **una vez por cuota** (marca para no repetir). |
| `AvisoPreviosJob` (ShedLock, diario) | Cuotas que vencen en N días y `aviso_enviado=false` → publica `pago.por-vencer` y marca. |
| `ResponsableJob` | Completa responsable de pago de cuotas creadas sin él (cuando `alumnos-ms` estaba caído). |
| `PagoOnlineService` (MVP simulado) | Permite al apoderado "pagar" desde la app: crea el pago con medio `ONLINE_SIMULADO` y misma lógica; deja el punto de integración preparado para una pasarela real. |
| `OutboxPublisher`, `LimpiezaJob` | Igual que en matrícula (`SKIP LOCKED`; limpieza diaria). |

### `messaging/` y `event/`
- `MatriculaEventConsumer`: `matricula.creada` → réplica + cuota de matrícula; `.confirmada` → pensiones; `.trasladada` → actualizar sección en réplica; `.retirada`/`.anulada` → anular cuotas pendientes. Todos con `eventos_procesados`.
- Eventos publicados (por outbox): `PagoConfirmadoEvent`, `PagoPorVencerEvent`, `PagoVencidoEvent`, `PagoAnuladoEvent`; clave = id de pago o de cuota.

### `controller/` — API `/api/v1/pagos`
| Endpoint | Función | Roles |
|---|---|---|
| `GET/POST/PUT /conceptos` · `/tarifas` · `/descuentos` | Catálogos | ADMIN (lectura SECRETARIA) |
| `POST /descuentos/asignar` | Asignar descuento a alumno/año | ADMIN |
| `GET /alumnos/{alumnoId}/estado-cuenta` | Cuotas y saldos | CAJERO, SECRETARIA, ADMIN, apoderado/alumno dueños |
| `GET /cuotas?estado=&seccionId=&vencimiento=` | Consulta paginada | CAJERO, SECRETARIA, ADMIN |
| `POST /` | Registrar pago (con `idempotencyKey`) | CAJERO, ADMIN |
| `POST /online` | Pago simulado desde la app | APODERADO |
| `GET /{id}` · `GET /{id}/comprobante` | Detalle y PDF | CAJERO, ADMIN, dueño |
| `PATCH /{id}/anular` | Anular | ADMIN |
| `POST /caja/abrir` · `POST /caja/cerrar` · `GET /caja/actual` | Caja | CAJERO |
| `GET /caja/{id}/reporte` | Cierre | CAJERO, ADMIN |
| `GET /morosos?seccionId=&gradoId=` | Morosidad | SECRETARIA, ADMIN |
| `GET /reportes/cobranza?desde=&hasta=` | Cobranza por periodo y medio | ADMIN |
| `GET /mias/cuotas` | Cuotas de los hijos del usuario | APODERADO |
| `/internal/pagos/alumno/{id}/deuda` | ¿Tiene deuda vencida? (ratificación) | sin Gateway |

### Pruebas clave
- Mismo `matricula.creada` dos veces → una sola cuota de matrícula.
- Mismo cobro con la misma `idempotencyKey` → un solo pago.
- Dos cajeros cobran la misma cuota a la vez → uno recibe 409.
- Serie de comprobante bajo concurrencia sin números repetidos ni saltos.
- Pago de matrícula publica `pago.confirmado` (outbox) aun con Kafka caído.
- Descuento aplicado y redondeo con diferencia en la última cuota.
- Cierre de caja con diferencia calculada.
- Dos réplicas: `VencimientoJob` corre una vez.

## 1.6 Huecos que cubre
H4, H8, H9, H10, H16, C1, C2, C4.

---

# PARTE 2 — `examenes-ms`

## 2.1 Qué hace
El docente arma un **banco de preguntas** por curso y programa exámenes en una ventana de fecha con duración; el alumno rinde el examen en línea; el sistema califica automáticamente lo objetivo y deja lo abierto para revisión del docente; al terminar publica la nota.

## 2.2 Qué NO hace
No decide la nota oficial del periodo (la usa `calificaciones-ms`), ni conoce a los alumnos de la sección (réplica `matriculas_local`).

## 2.3 Base de datos `examenes_db` (9 tablas)
| Tabla | Para qué |
|---|---|
| `matriculas_local` | Réplica por eventos. |
| `preguntas`, `opciones_pregunta` | Banco: tipos OPCION_UNICA, OPCION_MULTIPLE, VERDADERO_FALSO, ABIERTA; opciones con la correcta marcada. |
| `examenes` | Examen: sección, curso, periodo, docente, ventana, duración, intentos, estado BORRADOR/PUBLICADO/CERRADO/CALIFICADO. |
| `examen_preguntas` | Preguntas del examen con orden y puntaje (únicas por examen). |
| `intentos` | Intento de un alumno: inicio, **vence_en**, estado, puntaje y nota. |
| `respuestas` | Respuesta por pregunta (única por intento): opciones marcadas, texto, puntaje, bandera de revisión. |
| `eventos_procesados`, `shedlock` | Idempotencia y jobs. |

## 2.4 Archivos específicos

### `config/`
| Clase | Debe hacer |
|---|---|
| `SecurityConfig` | DOCENTE: banco y exámenes solo de sus cursos/secciones; ALUMNO: rendir solo los suyos, **sin ver respuestas correctas** mientras el examen esté abierto; APODERADO: ver notas de hijos; ADMIN. |
| `FeignConfig`, `Resilience4jConfig`, `CacheConfig` | Cliente a `planificacion-ms` (asignaciones, periodo, curso) con caché. |
| `Kafka*Config` | Produce `examen.publicado` y `examen.calificado`; consume `matricula.*` (`group-id=examenes-ms`). |
| `ShedLockConfig`, `SchedulingConfig` | Jobs. |
| `ExamenProperties` | Margen de gracia al enviar (segundos), tamaño máximo de examen, política de barajado. |

### `entity/` y `repository/`
`MatriculaLocal`, `Pregunta`, `OpcionPregunta`, `Examen`, `ExamenPregunta`, `Intento`, `Respuesta`, `EventoProcesado`.
Consultas: banco por curso/tema/tipo (paginado); exámenes por sección/estado/ventana; examen disponibles hoy para un alumno; intento activo del alumno; número de intentos usados; **intentos EN_CURSO vencidos**; respuestas de un intento; intentos con respuestas por revisar; agregados por examen (promedio, máximo, mínimo, distribución).

### `dto/` y `mapper/`
Para el **docente**: `PreguntaRequest/Response` (con opciones y correcta), `ExamenCreateRequest` (datos + lista de preguntas con puntaje), `ExamenResponse`, `RevisionRequest` (puntaje por respuesta abierta), `ResultadosExamenResponse`.
Para el **alumno** (DTO distinto, **sin campo `esCorrecta`**): `ExamenDisponibleResponse`, `IntentoIniciadoResponse` (preguntas en el orden asignado, `venceEn`), `GuardarRespuestaRequest`, `IntentoResultadoResponse` (nota y, si el docente lo permite, detalle).

### `service/`
| Servicio | Debe hacer |
|---|---|
| `MatriculaLocalService` | Igual que en los otros servicios. |
| `PreguntaService` | CRUD de preguntas del docente; valida que OPCION_UNICA tenga una correcta, MULTIPLE al menos una, VERDADERO_FALSO dos opciones; no se edita una pregunta usada en un examen ya publicado (se duplica). |
| `ExamenService` | Crear en BORRADOR; armar con preguntas y puntajes; **publicar**: valida asignación del docente, ventana futura, al menos una pregunta, recalcula `puntaje_total`, cambia a PUBLICADO y emite `examen.publicado` (con `alumnoIds`). Cerrar manualmente. |
| `IntentoService` (iniciar) | Valida ventana abierta, matrícula en la sección, intentos disponibles y que no haya otro EN_CURSO; calcula `vence_en = min(ahora + duración, fecha_fin)`; devuelve preguntas (barajadas por intento si se configuró) **sin respuestas correctas**. Si ya hay un intento EN_CURSO vigente, lo devuelve (reanudar tras corte de conexión). |
| `RespuestaService` | **Guardado incremental** de cada respuesta (UPSERT por intento+pregunta, rápido y liviano); rechaza si el intento venció (margen de gracia) o no es EN_CURSO. |
| `IntentoService` (enviar) | Marca ENVIADO, dispara calificación; si llega después de `vence_en` + gracia, cuenta solo lo guardado antes. |
| `CalificacionAutomaticaService` | Califica OPCION_UNICA/VF (todo o nada) y MULTIPLE (según regla configurada: todo o nada o proporcional); ABIERTA queda `requiere_revision`. Si no hay abiertas: calcula puntaje y **nota 0–20** (`puntaje/puntaje_total*20`), estado CALIFICADO y publica `examen.calificado`. |
| `RevisionService` | El docente puntúa las abiertas; al completar, calcula nota y publica `examen.calificado`. |
| `CierreIntentosJob` (ShedLock, cada minuto) | Cierra intentos EN_CURSO con `vence_en` vencido: los marca VENCIDO/ENVIADO y los califica con lo guardado. Sin este job, un alumno que cierra el navegador queda "en curso" para siempre. |
| `CierreExamenJob` (ShedLock) | Cuando pasa `fecha_fin`: cierra el examen y todos sus intentos abiertos; si todo está calificado → CALIFICADO. |
| `ResultadosService` | Estadísticas por examen y por pregunta (porcentaje de acierto) para el docente. |

### `messaging/` y `event/`
- `MatriculaEventConsumer` (réplica), `ExamenEventProducer`.
- `ExamenPublicadoEvent` (examenId, seccionId, cursoId, cursoNombre, titulo, fechaInicio, fechaFin, alumnoIds) y `ExamenCalificadoEvent` (examenId, intentoId, alumnoId, matriculaId, seccionId, cursoId, periodoId, titulo, nota 0–20). Se publica uno por alumno; si el docente corrige una abierta, se vuelve a publicar con la nueva nota.

### `controller/` — API `/api/v1/examenes`
| Endpoint | Función | Roles |
|---|---|---|
| `GET/POST/PUT/DELETE /preguntas` | Banco | DOCENTE (propias/de su curso), ADMIN |
| `POST /` · `PUT /{id}` · `PATCH /{id}/publicar` · `PATCH /{id}/cerrar` | Gestión de exámenes | DOCENTE asignado, ADMIN |
| `GET /` · `GET /{id}` | Listado y detalle (docente) | DOCENTE, ADMIN |
| `GET /{id}/resultados` | Estadísticas y notas | DOCENTE, ADMIN |
| `GET /{id}/pendientes-revision` · `PATCH /respuestas/{id}/revisar` | Revisión de abiertas | DOCENTE |
| `GET /disponibles` | Exámenes que el alumno puede rendir hoy | ALUMNO |
| `POST /{id}/intentos` | Iniciar o reanudar | ALUMNO |
| `PUT /intentos/{id}/respuestas/{preguntaId}` | Guardar respuesta | ALUMNO (dueño) |
| `POST /intentos/{id}/enviar` | Enviar examen | ALUMNO (dueño) |
| `GET /intentos/{id}/resultado` | Nota | ALUMNO, APODERADO |
| `GET /mias` | Exámenes y notas de hijos | APODERADO, ALUMNO |

### Pruebas clave
- El alumno nunca recibe las respuestas correctas antes de terminar.
- Enviar después de `vence_en` + gracia no cuenta respuestas tardías.
- Cierre de navegador: el job cierra el intento y lo califica.
- Dos pestañas del mismo alumno no crean dos intentos.
- 150 alumnos iniciando a la vez (prueba de carga ligera) sin errores.
- Pregunta abierta: el examen queda sin nota hasta revisar.
- Cambiar una pregunta usada en un examen publicado se rechaza.

## 2.5 Huecos que cubre
H1, H2, H6, H13, C7.

---

# PARTE 3 — `calificaciones-ms`

## 3.1 Qué hace
Es la **fuente oficial de notas**: define criterios de evaluación con pesos por curso y periodo, recibe notas manuales y notas de tareas y exámenes (por evento), calcula promedios ponderados, convierte a literal (AD/A/B/C en primaria), cierra periodos, genera boletines en PDF y calcula el resultado anual (aprobado/desaprobado/recuperación).

## 3.2 Qué NO hace
No crea tareas ni exámenes (solo recibe sus notas), no sabe quién está matriculado (réplica `matriculas_local`), y no gestiona recuperación, conducta ni orden de mérito en el MVP.

## 3.3 Base de datos `calificaciones_db` (11 tablas)
| Tabla | Para qué |
|---|---|
| `matriculas_local` | Réplica por eventos. |
| `parametros_evaluacion` | Nota máxima (20), mínima aprobatoria (11), decimales. |
| `escalas_literales` | Equivalencias AD/A/B/C por nivel (semilla de PRIMARIA). |
| `criterios_evaluacion` | Por periodo/sección/curso: nombre, **peso (%)** y origen (MANUAL, TAREAS, EXAMENES). |
| `notas` | Cada nota: matrícula, curso, periodo, criterio, valor 0–20, origen y `origen_id` (único por matrícula+criterio+origen+origen_id, evita duplicados). |
| `promedios_periodo` | Promedio ponderado por matrícula, curso y periodo (BORRADOR o CERRADO). |
| `cierres_periodo` | Estado del periodo por sección: ABIERTO, CERRADO, REABIERTO (con auditoría). |
| `boletines` | Versiones del boletín (PDF en MinIO), por periodo o anual. |
| `resultados_anuales` | Promedio final y estado por curso para decidir promoción. |
| `eventos_procesados`, `shedlock` | Idempotencia y jobs. |

## 3.4 Cómo se calcula (reglas)
1. El docente define criterios del curso y periodo (ej.: Tareas 30 %, Exámenes 50 %, Participación 20 %). **La suma debe ser 100 %** para poder cerrar.
2. Cada criterio puede alimentarse de notas manuales o de eventos: `tarea.calificada` → criterio de origen TAREAS; `examen.calificado` → criterio de origen EXAMENES.
3. **Un criterio de origen TAREAS/EXAMENES se calcula como el promedio simple** de las notas de ese origen en el periodo (regla del MVP; configurable a futuro).
4. Promedio del periodo = Σ (nota del criterio × peso/100). Se guarda con 2 decimales; el redondeo oficial a entero se aplica solo en el boletín.
5. Si un criterio no tiene notas todavía, se calcula con los criterios disponibles y el promedio queda BORRADOR; al **cerrar**, un criterio sin nota se rechaza (hay que ponerla o excluir al alumno).
6. Literal (primaria) según `escalas_literales`; aprobado si promedio ≥ nota mínima.
7. Promedio final del curso = promedio de los promedios de los periodos cerrados del año.

## 3.5 Archivos específicos

### `config/`
| Clase | Debe hacer |
|---|---|
| `SecurityConfig` | DOCENTE: criterios y notas solo de sus cursos/secciones; SECRETARIA: lectura, cierre y reapertura con ADMIN; ALUMNO/APODERADO: solo propio y **solo periodos cerrados** (o publicados). |
| `FeignConfig`, `Resilience4jConfig`, `CacheConfig` | Cliente a `planificacion-ms` (asignaciones, periodos, cursos, nivel) con caché larga (cambia poco). |
| `Kafka*Config` | Consume `matricula.*`, `tarea.calificada`, `examen.calificado` (`group-id=calificaciones-ms`); produce `boletin.generado`. |
| `StorageConfig` | MinIO, bucket `boletines`. |
| `PdfConfig` | Plantilla del boletín (logo, datos del colegio, tabla de cursos). |
| `ShedLockConfig`, `SchedulingConfig` | Jobs. |

### `entity/` y `repository/`
`MatriculaLocal`, `ParametroEvaluacion`, `EscalaLiteral`, `CriterioEvaluacion`, `Nota`, `PromedioPeriodo`, `CierrePeriodo`, `Boletin`, `ResultadoAnual`, `EventoProcesado`.
Consultas: criterios por periodo/sección/curso; suma de pesos; notas por matrícula y periodo; notas de un criterio; promedios por sección y curso; cierres por sección; boletín vigente más reciente; resultados por alumno y año; matrículas de una sección.

### `dto/` y `mapper/`
`CriterioRequest/Response`, `NotaRequest`, `NotasLoteRequest` (toda la sección de un criterio en una sola petición), `NotaResponse`, `RegistroNotasSeccionResponse` (matriz alumnos × criterios para el docente), `PromedioResponse`, `CierrePeriodoRequest`, `ReaperturaRequest` (motivo), `BoletinResponse`, `ResultadoAnualResponse`, `LibretaAlumnoResponse` (notas por curso/periodo).

### `service/`
| Servicio | Debe hacer |
|---|---|
| `MatriculaLocalService` | Réplica por eventos (igual que los otros). |
| `CriterioService` | CRUD de criterios del docente; valida asignación; no permite cambiar peso si el periodo está CERRADO; informa la suma de pesos. |
| `NotaService` | Registra o actualiza notas manuales (lote); valida que la matrícula pertenezca a la sección, valor 0–20 y periodo ABIERTO/REABIERTO; recalcula el promedio del alumno en el curso. |
| `NotaEventoService` | Procesa `tarea.calificada`/`examen.calificado`: busca el criterio de origen del curso/periodo/sección (si el docente no lo creó, guarda la nota en cola de "pendientes de asignar" visible al docente, **no la pierde**); UPSERT por (matrícula, criterio, origen, origen_id); idempotente por `eventId`; si el periodo está CERRADO, no modifica y registra una alerta para el docente. |
| `PromedioService` | Calcula el promedio ponderado según §3.4; actualiza literal; se dispara al registrar notas y por un recálculo completo bajo demanda. |
| `CierrePeriodoService` | Cerrar periodo por sección: verifica suma de pesos = 100 en todos los cursos, que todos los alumnos tengan notas completas, congela promedios (CERRADO), registra quién y cuándo. Reabrir: solo ADMIN con motivo; queda auditado. |
| `ResultadoAnualService` | Al cerrar el último periodo del año: calcula promedio final y estado por curso; aprobado/desaprobado/recuperación según reglas (MVP: recuperación si el promedio está entre 08 y 10,99). |
| `BoletinService` | Genera el PDF del periodo (o anual) con datos del alumno (de `alumnos-ms`, lote), cursos, notas/literal, promedio, asistencia (opcional, fase 2) y firma; lo guarda en MinIO; crea versión nueva si ya existía (la anterior pasa a ANULADO); publica `boletin.generado`. Solo si el periodo está CERRADO. |
| `ConsultaService` | Libreta del alumno, matriz para el docente, reportes de sección (promedios, desaprobados). |
| `LimpiezaJob` | Diario: purga `eventos_procesados` antiguos. |

### `messaging/` y `event/`
- `MatriculaEventConsumer`, `TareaCalificadaConsumer`, `ExamenCalificadoConsumer`.
- `BoletinEventProducer` → `BoletinGeneradoEvent` (boletinId, alumnoId, matriculaId, periodoId, periodoNombre).
- Copias propias de `TareaCalificadaEvent` y `ExamenCalificadoEvent` (campos del informe general §5.2).

### `controller/` — API `/api/v1/calificaciones`
| Endpoint | Función | Roles |
|---|---|---|
| `GET/POST/PUT/DELETE /criterios` | Criterios del curso y periodo | DOCENTE (asignado), ADMIN |
| `GET /criterios/validar?seccionId=&cursoId=&periodoId=` | Suma de pesos | DOCENTE, ADMIN |
| `GET /registro?seccionId=&cursoId=&periodoId=` | Matriz alumnos × criterios | DOCENTE, ADMIN |
| `PUT /notas/lote` · `PUT /notas/{id}` | Registrar o corregir notas | DOCENTE, ADMIN |
| `GET /pendientes-asignar` | Notas de tareas/exámenes sin criterio | DOCENTE |
| `POST /periodos/cerrar` · `POST /periodos/reabrir` | Cierre y reapertura | ADMIN, SECRETARIA (cierre); ADMIN (reapertura) |
| `GET /promedios?seccionId=&periodoId=` | Promedios de la sección | DOCENTE, SECRETARIA, ADMIN |
| `POST /boletines/generar` (alumno o sección) | Generar | SECRETARIA, ADMIN |
| `GET /boletines/{id}/url` · `GET /boletines?alumnoId=` | Descargar / listar | dueño, SECRETARIA, ADMIN |
| `GET /alumnos/{alumnoId}/libreta?anioId=` | Notas por curso y periodo | ALUMNO, APODERADO, SECRETARIA, ADMIN |
| `GET /resultados-anuales?seccionId=` | Resultados | SECRETARIA, ADMIN |
| `GET /mias` | Libretas de los hijos | APODERADO, ALUMNO |

### Pruebas clave
- La misma `tarea.calificada` dos veces no suma dos notas.
- Recalificar una tarea actualiza la nota (no crea otra).
- Nota llega antes de que el docente cree el criterio → queda en pendientes, no se pierde.
- No se puede cerrar el periodo con pesos ≠ 100 o notas faltantes.
- Alumno/apoderado no ve notas de un periodo aún no cerrado.
- Boletín nuevo versiona y anula el anterior.
- Cálculo ponderado con ejemplos fijos (pruebas con valores conocidos).
- Reapertura exige motivo y queda auditada.

## 3.6 Huecos que cubre
H1, H2, H5, H6, H7, C2, C4.

---

## 4. Eventos de Witmer (resumen)

| Produce | Consume |
|---|---|
| `pago.confirmado`, `pago.por-vencer`, `pago.vencido`, `pago.anulado` | `matricula.*` (pago, examenes, calificaciones) |
| `examen.publicado`, `examen.calificado` | `tarea.calificada`, `examen.calificado` (calificaciones) |
| `boletin.generado` | — |

## 5. Dependencias con los otros dos integrantes

| Con quién | Qué se acuerda |
|---|---|
| **Andre** | `matricula.creada` debe traer `nivelId`, `gradoId`, `alumnoNombre` y, si se conocen, el responsable de pago; `matricula.confirmada` dispara las pensiones; `pago.confirmado` lleva `conceptoTipo=MATRICULA` y `matriculaId`. `tarea.calificada` con `periodoId`, `cursoId`, nota 0–20. |
| **Hermes** | Endpoints internos de planificación (periodos, cursos, nivel, asignaciones) y alumnos (responsable de pago, hijos, lote de nombres) — adenda A2 y A3. Plantillas nuevas de notificación ya entregadas en V2. |

## 6. Lista de verificación de Witmer
1. Migraciones de las 3 bases corren y las apps arrancan con `validate`.
2. Cargar tarifas y descuentos de prueba; `matricula.creada` genera la cuota de matrícula.
3. Cobrar la matrícula en caja → `pago.confirmado` visible en Kafka UI; la matrícula pasa a MATRICULADO (Andre) y se generan las pensiones.
4. Cobro duplicado con la misma `idempotencyKey` → un solo pago; cobro concurrente → 409.
5. Apertura y cierre de caja con diferencia.
6. Examen: crear, publicar, rendir con corte de conexión, calificar; `examen.calificado` en Kafka UI.
7. Calificaciones: criterios al 100 %, notas de tarea y examen llegan solas, cierre de periodo, boletín en MinIO.
8. Con dos réplicas, los jobs (vencidos, avisos, cierre de intentos) no se duplican.
