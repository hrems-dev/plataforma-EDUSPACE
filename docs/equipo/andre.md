# Andre Fabricio — matrícula, asistencia y tareas

Servicios a cargo: **`matricula-ms`**, **`asistencia-ms`**, **`tareas-ms`** (paquete `pe.edu.upeu.eduspace.*`).
Matrícula es la pieza central de la vida del alumno en el colegio: decide *quién está en qué sección* y avisa al resto con eventos.
Asistencia y tareas son el día a día del aula y dependen de esa relación.

Pruebas automáticas aproximadas: matrícula 25 · asistencia 21 · tareas 21.

---

## 1. `matricula-ms` (puerto 8093 · `matricula_db` · 15443)

**Para qué sirve.** Registra la matrícula de un alumno en un año, grado y sección, controla que no se pase la capacidad y maneja todo su ciclo de vida.
Es el **dueño de la relación alumno ↔ sección**. No guarda datos personales (los pide a `alumnos-ms`), no define secciones (las pide a `planificacion-ms`) y no cobra (lo hace `pago-ms`).

**Ciclo de vida**
```
PRE_MATRICULA ──(pago de la matrícula)──► MATRICULADO ──► RETIRADO / TRASLADADO
      └──► ANULADO (vence o la secretaría la anula)
```
El cambio de sección no cambia el estado (sigue MATRICULADO con otra sección).

| Área | Qué hace | Endpoints (`/api/v1/matriculas/…`) |
|---|---|---|
| Campañas | Periodo de matrícula de un año: crear, abrir, cerrar | `campanias`, `PATCH campanias/{id}/abrir\|cerrar` |
| Matrícula | Crear (pre-matrícula), consultar, listar con filtros, ver las propias | `POST /`, `GET /`, `{id}`, `mias` |
| Cupos | Ocupación por sección (control atómico, sin sobrepasar la capacidad) | `cupos` |
| Cambios | Traslado de sección (mismo grado y año), retiro, anulación | `PATCH {id}/traslado\|retiro\|anular` |
| Requisitos | Documentos exigidos (DNI, partida, SIAGIE…) marcados como cumplidos | `PUT {id}/requisitos/{tipo}` |
| Ratificación | Matrícula masiva del año siguiente; valida deuda en `pago-ms` | `POST ratificacion` |
| Alumnos de una sección | Para docentes y personal | `seccion/{id}/alumnos` |
| Internos | Para otros servicios | `/internal/matriculas/seccion/{id}`, `/internal/matriculas/alumno/{id}` |

**Tablas:** `campanias_matricula`, `matriculas`, `cupos_seccion`, `matricula_requisitos`, `matricula_historial`, `eventos_procesados`, `outbox_eventos`, `shedlock`.
**Publica:** `matricula.creada`, `.confirmada`, `.trasladada`, `.retirada`, `.anulada`. **Consume:** `pago.confirmado` (pasa a MATRICULADO) y `pago.anulado`.
**Llama a:** `alumnos-ms`, `planificacion-ms`, `pago-ms`.
**Reglas:** solo se matricula con una campaña ABIERTA del año de la sección (una por año; una CERRADA no se reabre) y con el alumno ACTIVO; la sección debe tener capacidad configurada y cupo libre; la pre-matrícula vence (`venceEn`) y `VencimientoJob` la anula; el traslado es dentro del mismo grado; todo cambio queda en `matricula_historial`.
**Pantallas** (espacio *Matrícula*): Matrículas · Campañas · Cupos (y la acción «Matricular alumno» del Panel de aulas).
**Pendiente:** constancia de matrícula en PDF; que «aprobó el año» (ratificación) se consulte a `calificaciones-ms`.

---

## 2. `asistencia-ms` (puerto 8094 · `asistencia_db` · 15444)

**Para qué sirve.** Registra la asistencia por día (o por curso y bloque), tardanzas, justificaciones de los apoderados, correcciones auditadas y reportes. Avisa al apoderado de una falta.
No sabe quién pertenece a una sección (usa su réplica `matriculas_local`, alimentada por `matricula.*`) ni quién enseña qué (lo valida con `planificacion-ms`).

| Área | Qué hace | Endpoints (`/api/v1/asistencia/…`) |
|---|---|---|
| Sesiones | Abrir la sesión de un día/curso, consultar, cerrar | `sesiones` (POST/GET), `sesiones/{id}`, `PATCH sesiones/{id}/cerrar` |
| Registro | Marcar presente/falta/tardanza a toda la sección de una vez | `PUT sesiones/{id}/registro` |
| Correcciones | Cambiar un registro dejando auditoría | `PATCH detalle/{id}`, tabla `asistencia_auditoria` |
| Justificaciones | El apoderado pide justificar (con archivo en MinIO); personal aprueba/rechaza | `justificaciones` (POST/GET), `justificaciones/mias`, `PATCH justificaciones/{id}/resolver`, `…/archivo-url`, `…/archivo` |
| Reportes | Resumen del alumno, reporte de sección con alertas por exceso de faltas | `alumnos/{id}/resumen`, `secciones/{id}/reporte`, `mias` |

**Tablas:** `sesiones_asistencia`, `asistencia_detalle`, `justificaciones`, `asistencia_auditoria`, `matriculas_local`, `eventos_procesados`, `shedlock`.
**Publica:** `asistencia.falta-registrada` (una sola vez por falta), `justificacion.resuelta`. **Consume:** `matricula.*`.
**Llama a:** `alumnos-ms`, `planificacion-ms` (feriados, asignaciones), `matricula-ms`.
**Reglas:** solo se abre asistencia en días hábiles (no feriados ni fines de semana) y nunca en fechas futuras; la tardanza exige minutos; una sesión CERRADA solo la corrigen ADMIN/SECRETARIA con motivo y auditoría; el docente solo registra sus sesiones.
**Pantallas:** *Asistencia y notas → Justificaciones* y *Reporte de asistencia* (personal); *Asistencia* del docente; *Asistencia* de la familia. La ficha del Panel de aulas muestra el promedio y los alumnos con más faltas usando el reporte.

---

## 3. `tareas-ms` (puerto 8098 · `tareas_db` · 15448)

**Para qué sirve.** El docente publica tareas por curso y sección (con adjuntos), el alumno entrega archivos y comentarios, el docente califica con retroalimentación (0–20) y la nota viaja a `calificaciones-ms`.
No decide la nota oficial del periodo, no guarda archivos (MinIO) y usa su réplica `matriculas_local` para saber qué alumnos tiene la sección.

| Área | Qué hace | Endpoints (`/api/v1/tareas/…`) |
|---|---|---|
| Tareas | Crear, editar, publicar, cerrar; adjuntos por URL firmada | `POST /`, `PUT {id}`, `PATCH {id}/publicar\|cerrar`, `{id}/adjuntos/url` |
| Entregas | El alumno sube el archivo (URL firmada) y entrega; versiones y marca de entrega tardía | `{id}/entregas/archivo-url`, `POST {id}/entregas`, `GET {id}/entregas` |
| Calificación | Calificar o devolver una entrega; calificar en lote | `PATCH entregas/{id}/calificar\|devolver`, `PATCH {id}/calificaciones` |
| Consulta | Lo mío y lo pendiente | `mias`, `mias/pendientes`, `GET /` |

**Tablas:** `tareas`, `tarea_adjuntos`, `entregas`, `entrega_archivos`, `matriculas_local`, `eventos_procesados`, `shedlock`.
**Publica:** `tarea.publicada`, `tarea.entregada` (aún sin consumidor), `tarea.calificada`, `tarea.recordatorio` (job: avisa a quienes no han entregado). **Consume:** `matricula.*`.
**Llama a:** `planificacion-ms`, `matricula-ms`, `alumnos-ms`; MinIO para archivos.
**Reglas:** la tarea nace en BORRADOR y se publica con fecha de entrega futura; una PUBLICADA solo admite cambiar descripción y fecha; una CERRADA no se edita ni recibe entregas; el docente solo gestiona sus tareas y debe estar asignado a esa sección y curso; el periodo debe pertenecer al año de la sección y estar ACTIVO; nota 0–20 enviada a calificaciones con su `periodoId`.
**Pantallas:** *Tareas* del docente (con la lista de entregas y calificación) y *Tareas* de la familia.
**Pendiente:** un consumidor de `tarea.entregada` para avisar al docente.

---

## 4. Con quién se conecta
- **Hermes:** secciones, año, periodos, feriados, asignaciones (planificación) y datos de alumnos/apoderados (alumnos).
- **Witmer:** `pago.confirmado` activa la matrícula; `tarea.calificada` alimenta las notas.
- Todos los servicios de Witmer y asistencia/tareas dependen de los eventos `matricula.*` de este paquete.

## 5. Dónde está el código
`services/matricula-ms`, `services/asistencia-ms`, `services/tareas-ms` · configuración en `infra/eduspace-config/config-repo/` · informe original en `docs/03-andre-matricula-asistencia-tareas/`.
Nota histórica: según `INFORME-ESTADO.md`, estos tres servicios se construyeron completos en la etapa de integración a partir de su informe.
