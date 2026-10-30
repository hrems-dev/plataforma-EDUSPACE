# Witmer — pagos, exámenes y calificaciones

Servicios a cargo: **`pago-ms`**, **`examenes-ms`**, **`calificaciones-ms`** (paquete `pe.edu.upeu.eduspace.*`).
Pago es el dueño del dinero; exámenes y calificaciones cierran el ciclo académico: evaluar al alumno y producir la nota oficial y el boletín.

Pruebas automáticas aproximadas: pago 26 · exámenes 21 · calificaciones 16.

---

## 1. `pago-ms` (puerto 8096 · `pago_db` · 15446)

**Para qué sirve.** Es el dueño de todo el dinero: conceptos, tarifas, descuentos, cuotas por alumno, registro de pagos (caja o en línea simulado), comprobantes, caja, mora y avisos de vencimiento.
Cierra el flujo de matrícula: al cobrar la matrícula publica `pago.confirmado`. No decide quién está matriculado (usa `matriculas_local`) ni emite facturas SUNAT (los comprobantes son internos).

**Ciclo de una cuota:** `PENDIENTE → PARCIAL → PAGADA`; desde pendiente/parcial → `VENCIDA` (job diario, con mora); `ANULADA` si la matrícula se anula o se retira.

| Área | Qué hace | Endpoints (`/api/v1/pagos/…`) |
|---|---|---|
| Conceptos y tarifas | Qué se cobra (matrícula, pensión, otros) y cuánto por año/grado | `conceptos`, `tarifas` (CRUD) |
| Descuentos | Definir y asignar a alumnos | `descuentos`, `POST descuentos/asignar` |
| Cuotas | Matrícula y pensiones generadas por evento; estado de cuenta | `cuotas`, `alumnos/{id}/estado-cuenta`, `mias/cuotas` |
| Pagos | Cobro en caja (idempotente, comprobante único), pago en línea, consulta, anulación, comprobante PDF | `POST /`, `POST online`, `{id}`, `{id}/comprobante`, `PATCH {id}/anular` |
| Caja | Abrir, cerrar y reportar la sesión de caja | `caja/abrir`, `caja/cerrar`, `caja/actual`, `caja/{id}/reporte` |
| Morosos y reportes | Deuda vencida y cobranza | `morosos`, `reportes/cobranza` |
| Alertas | Avisos al administrador (p. ej. falta tarifa) | `alertas`, `PATCH alertas/{id}/resolver` |
| Interno | Deuda de un alumno (lo usa la ratificación) | `/internal/pagos/alumno/{id}/deuda` |

**Tablas:** `conceptos_pago`, `tarifas`, `descuentos`, `descuentos_alumno`, `cuotas`, `pagos`, `pago_detalle`, `comprobantes`, `series_comprobante`, `cajas_sesion`, `alertas_admin`, `matriculas_local`, `eventos_procesados`, `outbox_eventos`, `shedlock`.
**Publica:** `pago.confirmado`, `pago.anulado`, `pago.por-vencer`, `pago.vencido`. **Consume:** `matricula.*`.
**Llama a:** `alumnos-ms` (responsable de pago), `planificacion-ms` (año calendario de las cuotas).
**Reglas importantes**
- Al llegar `matricula.creada` se genera la cuota de matrícula; al confirmarse el pago se activan las 10 pensiones.
- El **monto de la tarifa de pensión es el total del año** y se reparte en `num_cuotas` (la diferencia de redondeo va a la última cuota). Confirmar con el colegio si prefieren cargar el monto mensual.
- Sin tarifa se crea una alerta; al crearla se generan las cuotas pendientes.
- Un pago en línea solo vale si el alumno es hijo del apoderado que paga y las cuotas son suyas (403 si no).
- Un comprobante por pago: consultar no consume correlativos.
- El monto de una tarifa existente no se modifica: se desactiva y se crea otra. Un cajero solo puede tener una caja ABIERTA; un pago de una caja ya cerrada no se anula sin autorización extraordinaria. Un alumno no recibe dos veces el mismo descuento en un año.
**Para la matrícula en línea (internos):** `GET /internal/tarifas/aplicable`, `GET /internal/pagos/matricula/{id}/cuotas` y `POST /internal/pagos/matricula-en-linea` (cobro con medio `ONLINE_SIMULADO`, idempotente por matrícula). Es el punto donde se integraría una pasarela real.
**Pantallas** (espacio *Finanzas*): Resumen · Cuentas por alumno · Cobranza · Morosos y alertas · Conceptos · Tarifas · Descuentos (ADMIN) · *Caja* (CAJERO) · *Pagos* de la familia (APODERADO).
**Pendiente:** conciliación de caja, pasarela real, SUNAT; el 409 de `descuentos/asignar` visto en una prueba corresponde a la regla «el alumno ya tiene ese descuento en el año» (por el código, no se reprodujo).

---

## 2. `examenes-ms` (puerto 8097 · `examenes_db` · 15447)

**Para qué sirve.** El docente arma un banco de preguntas por curso y programa exámenes con ventana de fechas y duración; el alumno rinde en línea; se califica automáticamente lo objetivo y lo abierto queda para revisión del docente; al terminar se publica la nota.

| Área | Qué hace | Endpoints (`/api/v1/examenes/…`) |
|---|---|---|
| Banco de preguntas | CRUD; solo el docente dueño edita o elimina | `preguntas` |
| Exámenes | Crear, publicar, cerrar, ver resultados | `POST /`, `PUT {id}`, `PATCH {id}/publicar\|cerrar`, `{id}/resultados` |
| Rendir | Iniciar intento, guardar respuestas, enviar; el **tiempo lo controla el servidor** | `POST {id}/intentos`, `PUT intentos/{id}/respuestas/{preguntaId}`, `POST intentos/{id}/enviar`, `intentos/{id}/resultado` |
| Revisión | Preguntas abiertas pendientes | `{id}/pendientes-revision`, `PATCH respuestas/{id}/revisar` |
| Consulta | Disponibles y míos | `disponibles`, `mias` |

**Tablas:** `preguntas`, `opciones_pregunta`, `examenes`, `examen_preguntas`, `intentos`, `respuestas`, `matriculas_local`, `eventos_procesados`, `shedlock`.
**Publica:** `examen.publicado`, `examen.calificado` (incluye `cursoNombre`). **Consume:** `matricula.*`.
**Llama a:** `alumnos-ms`, `planificacion-ms` (valida que el docente dicte el curso).
**Pantallas:** *Exámenes* y *Banco de preguntas* del docente; *Exámenes* de la familia, con la pantalla de rendir.
**Pendiente:** revisión de preguntas abiertas desde la web (falta el endpoint de respuestas para la pantalla); aleatorización y anti-copia (fase 2).

---

## 3. `calificaciones-ms` (puerto 8095 · `calificaciones_db` · 15445)

**Para qué sirve.** Es la **fuente oficial de notas**: define criterios con pesos por curso y periodo, recibe notas manuales y las de tareas y exámenes (por evento), calcula promedios, convierte a literal, cierra periodos, genera boletines PDF y el resultado anual.

**Cómo se calcula**
1. El docente define criterios por curso y periodo (p. ej. Tareas 30 %, Exámenes 50 %, Participación 20 %). La suma debe ser 100 % para poder cerrar.
2. Un criterio de origen TAREAS o EXAMENES es el promedio simple de las notas de ese origen en el periodo (llegan por `tarea.calificada` y `examen.calificado`; si todavía no se puede asignar a un criterio quedan en `notas_pendientes`).
3. Promedio del periodo = Σ (nota del criterio × peso / 100), con 2 decimales; el redondeo entero se aplica solo en el boletín.
4. Con criterios sin nota el promedio es BORRADOR; al **cerrar** el periodo no se admite un criterio vacío.
5. Literal de Primaria (AD/A/B/C) según `escalas_literales`; hoy los demás niveles caen a la escala de Primaria (decisión de producto pendiente).
6. Promedio final del curso = promedio de los periodos cerrados; el resultado anual sale de ahí.

| Área | Endpoints (`/api/v1/calificaciones/…`) |
|---|---|
| Criterios | `criterios` (CRUD), `criterios/validar` |
| Notas | `registro`, `PUT notas/lote`, `PUT notas/{id}`, `pendientes-asignar` |
| Periodos | `POST periodos/cerrar`, `POST periodos/reabrir` (auditada), `promedios`, `resultados-anuales` |
| Libretas y boletines | `alumnos/{id}/libreta`, `mias`, `POST boletines/generar`, `boletines`, `boletines/{id}`, `boletines/{id}/url` (PDF en MinIO) |

**Tablas:** `criterios_evaluacion`, `notas`, `notas_pendientes`, `promedios_periodo`, `cierres_periodo`, `boletines`, `resultados_anuales`, `escalas_literales`, `parametros_evaluacion`, `matriculas_local`, `eventos_procesados`, `shedlock`.
**Publica:** `boletin.generado`. **Consume:** `matricula.*`, `tarea.calificada`, `examen.calificado`.
**Llama a:** `alumnos-ms`, `planificacion-ms`.
**Pantallas:** *Asistencia y notas → Cierre de periodo* y *Libretas y boletines* (personal); *Notas* del docente; *Notas* y *Boletines* de la familia.
**Pendiente:** recuperación, conducta y orden de mérito (fase 2).

---

## 4. Con quién se conecta
- **Andre:** `matricula.*` alimenta las réplicas locales de los tres servicios; `pago.confirmado` activa la matrícula; `tarea.calificada` llega a calificaciones.
- **Hermes:** año, periodos y cursos (planificación), datos de alumno y responsable de pago (alumnos) y avisos (notificaciones consume `pago.*`, `examen.*` y `boletin.generado`).

## 5. Dónde está el código
`services/pago-ms`, `services/examenes-ms`, `services/calificaciones-ms` · configuración en `infra/eduspace-config/config-repo/` · informe original en `docs/04-witmer-pago-examenes-calificaciones/`.
Nota histórica: según `INFORME-ESTADO.md` y `docs/05-integracion-y-correcciones.md`, estos servicios se corrigieron en la etapa de integración (eventos planos, comprobantes, cuotas, identidad, PDF, pendientes persistentes, outbox).
