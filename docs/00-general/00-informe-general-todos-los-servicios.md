# EduSpace — Informe general de todos los servicios

**Proyecto:** EduSpace — "Todo a la mano" · DIST 2026-2 (UPeU) · Base: arquitectura **pagatu**
**Equipo:** Hermes · Andre Fabricio · Witmer
**Para qué sirve este documento:** es el mapa único del sistema. Dice qué hace cada servicio, qué base de datos tiene, cómo se conectan, y recoge la **revisión de huecos y cuellos de botella** hecha sobre los 10 servicios y la infraestructura. Cada integrante tiene su informe detallado (archivo por archivo) en su carpeta.

> Todo el material describe lo que **debe hacer** el código; no incluye código.

---

## 1. Contenido del paquete

| Carpeta | Qué contiene | Quién lo usa |
|---|---|---|
| `00-general/` | Este informe (mapa, dependencias, huecos, estándares) | Todos |
| `01-infraestructura-inicial/` | Config Server, Eureka, Gateway, auth-ms, Kafka, MinIO, observabilidad, config-repo + SQL de `auth_db` | Todos (semana 1, en equipo) |
| `02-hermes-…/` | Informe ya entregado (planificación, alumnos, notificaciones) + **adenda** de correcciones + SQL | Hermes |
| `03-andre-…/` | Informe de `matricula-ms`, `asistencia-ms`, `tareas-ms` + SQL | Andre Fabricio |
| `04-witmer-…/` | Informe de `pago-ms`, `examenes-ms`, `calificaciones-ms` + SQL | Witmer |
| `99-anexos-historicos/` | Documento de distribución inicial (6 servicios). **Reemplazado** por este paquete; se conserva por referencia | — |

Orden de lectura sugerido: este informe → infraestructura → el informe propio → adenda (Hermes).

---

## 2. Mapa de los 10 servicios

| # | Servicio | Qué hace (en una frase) | BD | Tablas | Puerto DEV | Responsable |
|---|---|---|---|---|---|---|
| 1 | `auth-ms` | Login, emisión de JWT RS256, usuarios y roles | `auth_db` | 4 | 8090 | Equipo |
| 2 | `planificacion-ms` | Año, periodos, grados, secciones, aulas, cursos, docentes y horario | `planificacion_db` | 14 | 8091 | Hermes |
| 3 | `alumnos-ms` | Expediente del alumno, apoderados, documentos, salud | `alumnos_db` | 6 | 8092 | Hermes |
| 4 | `matricula-ms` | Matricular alumno en año/grado/sección con control de cupos | `matricula_db` | 8 | 8093 | Andre |
| 5 | `asistencia-ms` | Asistencia diaria o por curso, justificaciones | `asistencia_db` | 7 | 8094 | Andre |
| 6 | `calificaciones-ms` | Notas, promedios, cierre de periodo, boletines | `calificaciones_db` | 11 | 8095 | Witmer |
| 7 | `pago-ms` | Tarifas, cuotas, pagos, caja, comprobantes | `pago_db` | 14 | 8096 | Witmer |
| 8 | `examenes-ms` | Banco de preguntas, exámenes en línea, calificación | `examenes_db` | 9 | 8097 | Witmer |
| 9 | `tareas-ms` | Tareas, entregas con archivos, calificación | `tareas_db` | 7 | 8098 | Andre |
| 10 | `notificaciones-ms` | Bandeja, plantillas, correo, push | `notificaciones_db` | 5 (+2 en V2) | 8099 | Hermes |

BD PostgreSQL por servicio en puertos 15440–15449 (DEV) y 25440–25449 (PROD local). Infraestructura: Config 18888, Eureka 18761, Gateway 18080, Kafka 19092, Kafka UI 18085, Prometheus 19090, Loki 13100, Grafana 13000, MinIO 19000/19001, Mailpit 11025/18025.

Total: **85 tablas** en las migraciones V1 de las 10 bases, y **87** con la V2 de notificaciones.

### 2.1 Quién es dueño de cada dato (fuente de verdad)

| Dato | Dueño | Quién lo copia (y cómo) |
|---|---|---|
| Usuario / rol / contraseña | `auth-ms` | Los demás guardan solo `usuario_id` |
| Año, periodo, grado, sección, curso, docente, horario | `planificacion-ms` | Otros guardan ids; consultan por Feign con caché |
| Alumno, apoderado, documentos | `alumnos-ms` | Otros guardan `alumno_id` + nombre (copia) |
| **Matrícula** (alumno ↔ sección) | `matricula-ms` | `asistencia`, `tareas`, `examenes`, `calificaciones`, `pago` mantienen `matriculas_local` por eventos |
| Cupos de sección | `matricula-ms` | — |
| Tarifas, cuotas, pagos, comprobantes | `pago-ms` | — |
| Asistencia | `asistencia-ms` | — |
| Notas oficiales, promedios, boletines | `calificaciones-ms` | Recibe notas de `tareas` y `examenes` por eventos |
| Notificaciones | `notificaciones-ms` | — |

---

## 3. Cómo se conectan

### 3.1 Llamadas síncronas (Feign + circuit breaker + caché)

| Origen → Destino | Para qué | Si el destino cae |
|---|---|---|
| matricula → alumnos | Validar que el alumno existe, está ACTIVO y tiene expediente completo | No se puede matricular (error claro 503) |
| matricula → planificacion | Año activo, datos y capacidad de la sección (para crear `cupos_seccion`) | Usa `cupos_seccion` ya cargado; si no existe, 503 |
| pago → alumnos | Responsable de pago del alumno | Cuota se crea sin responsable; se completa luego (reintento) |
| asistencia / tareas / examenes → planificacion | ¿El docente está asignado a esa sección y curso? ¿El día es hábil? | **Deniega** la escritura (seguridad primero); lecturas siguen |
| asistencia / tareas / calificaciones / pago → alumnos | "¿Qué hijos tiene este apoderado?" (para permisos) | Caché 5 min; si no hay caché, deniega |
| notificaciones → alumnos | Contactos (alumno y apoderados) a notificar | La notificación queda PENDIENTE y se reintenta |
| calificaciones → planificacion | Cursos, periodos, escala por nivel | Caché larga (cambia poco) |

Reglas: toda llamada Feign lleva `X-Trace-ID`, timeout corto (2–3 s), circuit breaker en bean aparte y fallback explícito. **Ningún servicio llama a otro dentro de una transacción de base de datos larga.**

### 3.2 Eventos Kafka (asíncronos)

Sobre estándar de todos los eventos (ver §5.1). Catálogo completo:

| Tópico | Productor | Consumidores |
|---|---|---|
| `alumno.registrado` | alumnos | (futuro: auth para crear cuenta) |
| `alumno.estado-cambiado` | alumnos | (ninguno en el MVP; disponible para fase 2) |
| `anio.activado` · `periodo.cambiado` | planificacion | (ninguno en el MVP; disponibles para fase 2) |
| `horario.publicado` | planificacion | notificaciones |
| `matricula.creada` | matricula | pago, asistencia, tareas, examenes, calificaciones, notificaciones |
| `matricula.confirmada` | matricula | pago (genera pensiones), notificaciones, + los de la réplica |
| `matricula.trasladada` | matricula | pago, asistencia, tareas, examenes, calificaciones |
| `matricula.retirada` · `matricula.anulada` | matricula | pago (anula cuotas pendientes), + réplicas, notificaciones |
| `pago.confirmado` | pago | **matricula** (concepto MATRICULA), notificaciones |
| `pago.por-vencer` · `pago.vencido` | pago | notificaciones |
| `pago.anulado` | pago | matricula (alerta a secretaría; decisión manual) |
| `asistencia.falta-registrada` | asistencia | notificaciones |
| `tarea.publicada` | tareas | notificaciones |
| `tarea.entregada` | tareas | (auditoría / futuro) |
| `tarea.calificada` | tareas | **calificaciones**, notificaciones |
| `examen.publicado` | examenes | notificaciones |
| `examen.calificado` | examenes | **calificaciones**, notificaciones |
| `boletin.generado` | calificaciones | notificaciones |

### 3.3 Flujo principal: matrícula con pago (coreografía)

1. Secretaría crea la matrícula → `matricula-ms` reserva cupo (UPDATE atómico), guarda en estado `PRE_MATRICULA` con `vence_en` (p. ej. 7 días) y escribe `matricula.creada` en su **outbox**.
2. `pago-ms` consume `matricula.creada` → crea la cuota de **Matrícula** (tarifa del nivel/grado, descuento si corresponde). `notificaciones-ms` avisa al apoderado.
3. Caja cobra → `pago-ms` registra el pago, baja el saldo, emite comprobante y publica `pago.confirmado` (concepto MATRICULA, `matriculaId`).
4. `matricula-ms` consume `pago.confirmado` → pasa a `MATRICULADO`, publica `matricula.confirmada`.
5. `pago-ms` consume `matricula.confirmada` → genera las cuotas de **pensión** del año. Las demás réplicas pasan la matrícula a `MATRICULADO`.
6. **Camino de fallo:** si vence `vence_en` sin pago → un job (con ShedLock) anula la matrícula, libera el cupo y publica `matricula.anulada`; `pago-ms` anula la cuota pendiente.

---

## 4. Revisión de huecos y cuellos de botella

Se revisó cada servicio y la infraestructura preguntando: *¿le falta una función para funcionar de punta a punta? ¿hay un punto que todos usan y se vuelve lento o se cae? ¿se pierde o se duplica información?* Resultado: **33 hallazgos** (17 huecos de funcionalidad y 16 cuellos de botella/riesgos). Los marcados 🔴 deben resolverse antes de integrar; 🟠 durante el desarrollo; 🟡 pueden esperar.

### 4.1 Huecos de funcionalidad

| # | Sev. | Hallazgo | Dónde | Solución adoptada |
|---|---|---|---|---|
| H1 | 🔴 | **Nadie sabe qué alumnos hay en una sección**: asistencia, tareas, exámenes y notas necesitan la lista, y solo `matricula-ms` la tiene. Llamarla en cada operación la convierte en cuello de botella. | matricula ↔ todos | Cada consumidor mantiene **`matriculas_local`** (réplica por eventos `matricula.*`, con nombre del alumno). Ya está en los SQL. |
| H2 | 🔴 | **Autorización del docente**: ¿puede este docente pasar lista / calificar / crear examen en *esta* sección y *este* curso? Solo `planificacion-ms` lo sabe, y el token trae `usuarioId`, no `docenteId`. | planificacion → asistencia, tareas, examenes, calificaciones | Endpoint interno `GET /internal/docentes/usuario/{usuarioId}/asignaciones` con caché de 60 s + circuit breaker; si no hay respuesta, se **deniega**. (Adenda Hermes A2) |
| H3 | 🔴 | **Autorización del apoderado/alumno**: "¿este apoderado puede ver las cuotas del alumno 57?" Ni el token ni los otros servicios lo saben. | alumnos → pago, calificaciones, asistencia, tareas | Endpoints internos `GET /internal/apoderados/usuario/{usuarioId}/alumnos` y `/internal/alumnos/usuario/{usuarioId}` con caché de 5 min. (Adenda Hermes A3) |
| H4 | 🔴 | **Pago de matrícula y matrícula no estaban conectados**: faltaba el vencimiento de la pre-matrícula y qué hacer si no paga. | matricula ↔ pago | Flujo §3.3 con `vence_en`, job de anulación y evento `matricula.anulada`. |
| H5 | 🟠 | **Cursos sin criterios de nota**: `calificaciones-ms` no puede promediar tareas y exámenes si no hay pesos definidos. | calificaciones | Tabla `criterios_evaluacion` (por periodo/sección/curso) con pesos que suman 100; el cierre de periodo **exige** suma = 100. |
| H6 | 🟠 | **Eventos de nota sin periodo/curso**: `tarea.calificada` y `examen.calificado` deben incluir `periodoId`, `cursoId`, `seccionId`, `matriculaId`, `nota` y escala. | tareas, examenes → calificaciones | Se fija en el contrato (§5.2). Tareas y exámenes guardan `periodo_id`. |
| H7 | 🟠 | **Cierre de año / promoción**: nadie decía quién decide aprobado/desaprobado y cómo se ratifica la matrícula del año siguiente. | calificaciones, matricula | `resultados_anuales` en calificaciones; ratificación en matricula (`tipo=RATIFICACION`, `matricula_anterior_id`) que consulta deuda a pago. |
| H8 | 🟠 | **Cobro duplicado**: doble clic o reintento de red en caja cobra dos veces. | pago | `idempotency_key` único por pago + bloqueo optimista (`version`) en cuotas. |
| H9 | 🟠 | **Numeración de comprobantes**: dos cajeros emitiendo a la vez pueden repetir número. | pago | `series_comprobante` con `SELECT … FOR UPDATE` y restricción única (tipo, serie, número). |
| H10 | 🟠 | **Cierre de caja**: sin apertura/cierre no se concilia el efectivo del día. | pago | `cajas_sesion` (una abierta por cajero), diferencia declarado vs. sistema. |
| H11 | 🟠 | **Asistencia por curso en secundaria**: el diseño original solo contemplaba "por día". | asistencia | `sesiones_asistencia.tipo` DIA / CURSO con bloque. |
| H12 | 🟠 | **Justificaciones**: faltaban solicitud del apoderado, resolución y efecto sobre las faltas. | asistencia | Tabla `justificaciones`; al aprobar, las faltas del rango pasan a JUSTIFICADO con auditoría. |
| H13 | 🟠 | **Examen con límite de tiempo**: confiar en el reloj del navegador permite hacer trampa. | examenes | `intentos.vence_en` calculado en servidor; al enviar se rechaza lo posterior; job cierra intentos vencidos. |
| H14 | 🟡 | **Recuperación / notas de conducta / orden de mérito** | calificaciones | Fase 2. Se deja `estado=RECUPERACION` en resultados. |
| H15 | 🟡 | **Panel del director** (morosidad por sección, asistencia por grado…) mezcla datos de varios servicios. | varios | Fase 2: vistas propias alimentadas por eventos, **no** joins entre bases. |
| H16 | 🟡 | **Pasarela de pago real y facturación SUNAT** | pago | Fuera del MVP. Medio `ONLINE_SIMULADO`; comprobante interno. |
| H17 | 🟡 | **Alta de cuentas** de alumno/apoderado/docente | auth, alumnos | MVP manual (admin crea usuario y lo vincula); fase 2 por evento `alumno.registrado`. |

### 4.2 Cuellos de botella y riesgos de arquitectura

| # | Sev. | Hallazgo | Impacto | Solución adoptada |
|---|---|---|---|---|
| C1 | 🔴 | **Se pueden perder eventos** si el servicio cae entre el commit y el envío a Kafka (publicar "después del commit" no es atómico). Para matrícula y pago rompe el flujo §3.3. | Matrícula pagada que nunca se confirma | **Outbox transaccional** en `matricula-ms` y `pago-ms` (tabla `outbox_eventos`, publicador con `FOR UPDATE SKIP LOCKED`). Resto de servicios: después del commit. |
| C2 | 🔴 | **Eventos duplicados** (Kafka entrega al menos una vez) suman dos veces una nota, crean dos cuotas o notifican dos veces. | Datos incorrectos | Todo evento lleva `eventId`; cada consumidor guarda `eventos_procesados`; además restricciones únicas en BD (`uk_cuota`, `uk_nota_origen`, `uk_notif_evento`). |
| C3 | 🔴 | **auth-ms genera la clave RSA al arrancar**: con 2 réplicas cada una firma con otra clave, y al reiniciar todos los tokens quedan inválidos. | Usuarios expulsados al desplegar | En PROD la clave se **carga** (archivo/secret) con `kid` fijo; la generación al arrancar queda solo para DEV. Rotación = publicar dos claves en el JWKS. |
| C4 | 🟠 | **Jobs programados en varias réplicas** (vencidos, recordatorios, reintentos, cierre de intentos) se ejecutan N veces. | Moras y avisos duplicados | **ShedLock** (tabla `shedlock`) en pago, notificaciones, asistencia, tareas, calificaciones, examenes y matricula. |
| C5 | 🟠 | **`planificacion-ms` y `alumnos-ms` son consultados por todos.** | Si caen o se enlentecen, arrastran al resto | Lecturas internas simples; **caché local (Caffeine)** en consumidores; circuit breaker; réplicas locales por eventos donde el dato cambia poco. |
| C6 | 🟠 | **Última vacante**: dos secretarias matriculan a la vez en una sección con 1 cupo. | Sección sobrepoblada | `UPDATE cupos_seccion SET ocupados=ocupados+1 WHERE ocupados<capacidad` (0 filas ⇒ sin cupo) + restricción `ocupados<=capacidad`. |
| C7 | 🟠 | **Inicio simultáneo de un examen** (30–150 alumnos a la vez) y guardado de cada respuesta. | Picos de escritura | Guardado incremental por UPSERT (`uk_respuesta`), respuestas livianas, índice parcial de intentos en curso; examen con ventana amplia. |
| C8 | 🟠 | **Subida de archivos por el servicio** (tareas, documentos) satura memoria/red. | Servicio lento | El cliente sube **directo a MinIO con URL firmada**; el servicio solo registra metadatos y valida tipo/tamaño. Cuidar que el host de la URL firmada sea el accesible desde el navegador. |
| C9 | 🟠 | **Gateway**: falta CORS para Angular (4200), Electron y Flutter web; límite de tamaño de peticiones; punto único de falla. | Clientes bloqueados | CORS por configuración, límites multipart, y en PROD 2 réplicas detrás de un balanceador. (Ver informe de infraestructura) |
| C10 | 🟠 | **Config Server con `optional:`**: si está caído, el servicio arranca con valores por defecto y falla de forma confusa. | Errores difíciles de rastrear | En PROD usar `fail-fast` y reintentos de conexión; `optional:` solo en DEV. |
| C11 | 🟡 | **Zonas horarias**: servidor en UTC, colegio en America/Lima; vencimientos "a medianoche" salen un día antes/después. | Moras mal calculadas | Estándar: `TZ=America/Lima` en contenedores, `hibernate.jdbc.time_zone=America/Lima`, fechas de vencimiento como `DATE`. |
| C12 | 🟡 | **La app móvil del apoderado** necesita datos de 4–5 servicios para una pantalla. | Muchas llamadas | MVP: llamadas paralelas desde el cliente. Fase 2: BFF liviano. |
| C13 | 🟡 | **Colas atascadas por un mensaje malformado** | Consumidor detenido | `ErrorHandlingDeserializer` + reintentos acotados + tópico de errores (`*.DLT`) a partir de S9. |
| C14 | 🟡 | **Llamadas `/internal/**` sin token** | Cualquier contenedor de la red podría llamarlas | Aceptable en la red Docker del MVP; endurecer con token de servicio en la etapa de seguridad final. |
| C15 | 🟡 | **Datos de menores en logs y eventos** | Privacidad | Eventos y logs llevan ids y nombre; **nunca** salud, documentos ni DNI. |
| C16 | 🟡 | **Crecimiento de tablas técnicas** (`eventos_procesados`, `outbox_eventos`, `intentos_envio`) | Almacenamiento | Job de limpieza (ShedLock) que borra procesados con más de 30 días. |

### 4.3 Matriz: ¿cada servicio tiene lo necesario para funcionar?

| Servicio | Necesita para funcionar | ¿Cubierto? | Observación |
|---|---|---|---|
| auth-ms | Nada | ✔ | Falta clave persistente en PROD (C3) |
| planificacion-ms | Nada | ✔ con adenda | Endpoints internos nuevos (H2) |
| alumnos-ms | MinIO | ✔ con adenda | Endpoints internos nuevos (H3) |
| matricula-ms | alumnos, planificacion, pago (evento) | ✔ | Outbox y job de vencimiento |
| asistencia-ms | matriculas_local, planificacion | ✔ | Depende de eventos de matrícula |
| tareas-ms | matriculas_local, planificacion, MinIO | ✔ | — |
| examenes-ms | matriculas_local, planificacion | ✔ | — |
| calificaciones-ms | matriculas_local, eventos de tareas/exámenes, MinIO | ✔ | Necesita criterios definidos por el docente (H5) |
| pago-ms | matriculas_local, alumnos | ✔ | Outbox + ShedLock |
| notificaciones-ms | alumnos, todos los eventos | ✔ con adenda | V2: ShedLock, eventos_procesados, plantillas nuevas |

**Conclusión:** con los ajustes de las adendas el sistema queda **completo para el MVP** y sin servicios huérfanos. Los riesgos reales están en la integración por eventos (C1, C2) y en las dependencias de lectura hacia planificación y alumnos (C5); ambos están resueltos con los estándares de §5.

---

## 5. Estándares obligatorios para los 10 servicios

### 5.1 Sobre estándar de todo evento Kafka
Todo evento JSON incluye en el nivel superior:

| Campo | Tipo | Uso |
|---|---|---|
| `eventId` | UUID | Idempotencia (consumidor guarda en `eventos_procesados`) |
| `eventType` | texto | Igual al nombre del tópico |
| `occurredAt` | fecha-hora ISO | Cuándo ocurrió |
| `version` | entero | Versión del contrato (empieza en 1) |
| `traceId` | texto | Continuidad de trazas |

Después vienen los campos del negocio (§5.2). Clave del mensaje = id de la entidad principal (garantiza orden por entidad).

### 5.2 Campos mínimos de los eventos clave

| Evento | Campos de negocio |
|---|---|
| `matricula.creada` / `.confirmada` / `.trasladada` / `.retirada` / `.anulada` | matriculaId, alumnoId, alumnoNombre, anioId, nivelId, gradoId, seccionId, estado, tipo, (trasladada: seccionAnteriorId), motivo (retirada/anulada), `responsablePagoUsuarioId` y `responsablePagoNombre` (si se conocen) |
| `pago.confirmado` | pagoId, alumnoId, matriculaId, cuotaIds[], conceptoTipo (MATRICULA/PENSION/OTRO), monto, concepto |
| `pago.por-vencer` / `pago.vencido` | cuotaId, alumnoId, matriculaId, concepto, monto, saldo, fechaVencimiento |
| `asistencia.falta-registrada` | asistenciaDetalleId, alumnoId, matriculaId, seccionId, fecha, cursoId (si aplica) |
| `tarea.publicada` | tareaId, seccionId, cursoId, cursoNombre, titulo, fechaEntrega, alumnoIds[] |
| `tarea.calificada` | tareaId, entregaId, alumnoId, matriculaId, seccionId, cursoId, periodoId, titulo, nota, puntajeMaximo |
| `examen.publicado` | examenId, seccionId, cursoId, cursoNombre, titulo, fechaInicio, fechaFin, alumnoIds[] |
| `examen.calificado` | examenId, intentoId, alumnoId, matriculaId, seccionId, cursoId, periodoId, titulo, nota |
| `boletin.generado` | boletinId, alumnoId, matriculaId, periodoId (nulo = anual), periodoNombre |

La **nota** viaja en escala 0–20. Si la tarea tiene otro puntaje máximo, el productor la convierte antes de publicar.

### 5.3 Estructura interna de cada servicio
Igual que pagatu: `config / controller / dto / entity / exception / filter / mapper / repository / service (interfaz + Impl) / client / messaging / event`, más `application.yml` mínimo, `logback-spring.xml`, `db/migration/V1__*.sql`, `Dockerfile`, `compose-dev.yml`, `compose.yml`, `.env`/`.env.example`. Detalle por archivo en cada informe.

Añadidos del paquete (cuando el servicio los necesita):
- `config/ShedLockConfig` — habilita el bloqueo de tareas programadas.
- `messaging/OutboxPublisher` + `entity/OutboxEvento` — solo matricula y pago.
- `messaging/*Consumer` con verificación de `eventos_procesados`.
- `config/CacheConfig` — caché Caffeine para consultas a planificación/alumnos.

### 5.4 Convenciones de API
- Prefijo `/api/v1/<recurso>`; rutas internas `/internal/...` (no enrutadas por el Gateway).
- Listas paginadas (`page`, `size`, `sort`) y respuesta uniforme `PageResponse`.
- Errores con cuerpo uniforme: `codigo, mensaje, ruta, traceId, fecha, detalles[]`. Códigos: 400 validación, 401 sin token, 403 sin permiso, 404 no existe, 409 regla de negocio/duplicado, 503 dependencia caída.
- Fechas ISO-8601; dinero `NUMERIC(10,2)` en soles (PEN); notas `NUMERIC(5,2)`.
- Crear → 201 con `Location`; eliminar → 204.

### 5.5 Seguridad
JWT RS256 de `auth-ms`; Gateway valida y aplica rol grueso; cada servicio vuelve a validar el JWT (resource server) y aplica la regla fina (propiedad del dato). Claims: `sub`, `preferred_username`, `email`, `usuarioId`, `realm_access.roles`. Roles: `ADMIN`, `SECRETARIA`, `DOCENTE`, `ALUMNO`, `APODERADO`, `CAJERO`.

### 5.6 Rutas del Gateway (completas)

| Servicio | Predicado |
|---|---|
| auth-ms | `/api/v1/auth/**` |
| planificacion-ms | `/api/v1/planificacion/**` |
| alumnos-ms | `/api/v1/alumnos/**`, `/api/v1/apoderados/**` |
| matricula-ms | `/api/v1/matriculas/**` |
| asistencia-ms | `/api/v1/asistencia/**` |
| tareas-ms | `/api/v1/tareas/**` |
| examenes-ms | `/api/v1/examenes/**` |
| calificaciones-ms | `/api/v1/calificaciones/**` |
| pago-ms | `/api/v1/pagos/**` |
| notificaciones-ms | `/api/v1/notificaciones/**` |

---

## 6. Plan de trabajo sugerido

| Fase | Qué | Quién |
|---|---|---|
| 0 (equipo) | Repos, `config-server`, `eureka`, `gateway`, `auth-ms`, Kafka/MinIO/Mailpit | Todos |
| 1 | `planificacion-ms` + `alumnos-ms` (con adenda) · `matricula-ms` (sin eventos) · `pago-ms` (tarifas y cuotas manuales) | Hermes / Andre / Witmer |
| 2 | Eventos de matrícula y pago (outbox) + `matriculas_local` en todos | Andre / Witmer |
| 3 | `asistencia-ms`, `tareas-ms`, `examenes-ms` | Andre / Witmer |
| 4 | `calificaciones-ms` (consume tarea/examen), boletín PDF | Witmer |
| 5 | `notificaciones-ms` consumiendo todo | Hermes |
| 6 | Observabilidad, Docker PROD, pruebas punta a punta | Todos |

Prueba de aceptación final: **crear alumno → matricular → pagar matrícula → ver pensiones → pasar lista → publicar tarea → entregar y calificar → rendir examen → cerrar periodo → generar boletín → ver todo en notificaciones.**
