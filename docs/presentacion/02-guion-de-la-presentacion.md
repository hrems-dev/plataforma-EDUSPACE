# Guion de la presentación (45 min)

> **¿Poco tiempo?** Usa la [demostración rápida de 10 minutos](05-demo-rapida.md) (cinco procesos: nuevo alumno y aprobación de matrícula, profesor en clase, alumna con tarea y examen, apoderada, administración y finanzas).

Formato: **tiempo · quién · qué dice · qué ejecuta/muestra**. Los comandos se ejecutan desde la raíz del repositorio con el sistema levantado (`docs/presentacion/00-…` §5).

## Antes de empezar (nadie habla)
- Pantalla compartida con 5 pestañas: **sitio público**, **portal** (admin), **Mailpit** (`:18025`), **Kafka UI** (`:18085`), **terminal** (3 pestañas, una por demo).
- Sesión iniciada como `admin`. Zoom del navegador al 110 %.

---

## 0–3 min · Moderador — El problema y la idea
- **Dice:** el colegio lleva matrícula, asistencia, notas, cobros y horarios por separado; las familias no se enteran a tiempo. EduSpace son **10 microservicios** que se hablan por API y eventos.
- **Muestra:** diapositiva con las cifras (10 servicios, ~316 endpoints, 25 tópicos, ~280 pruebas).
- **Cierra con la frase de la narrativa:** «una familia matricula desde la web y todo ocurre por transacciones locales + eventos; si algo falla, el sistema compensa».

## 3–6 min · Moderador — Arquitectura y plataforma en vivo
- **Dice:** Gateway único, Config Server, Eureka, base por servicio, Kafka con outbox, Keycloak/auth-ms.
- **Ejecuta:** abre `http://localhost:18761` (Eureka: 11 aplicaciones) y muestra el diagrama de `docs/sistema/arquitectura.md`.
- **Transición:** «ahora cada uno defiende sus servicios con un proceso transaccional real».

---

## 6–15 min · HERMES — Planificación, alumnos, notificaciones
**Minutos 6–8 · Qué hace su parte**
- Planificación = fuente de verdad (año, secciones, malla, docentes, horarios); alumnos = expediente; notificaciones = único servicio que habla con las familias.
- Muestra el **Panel de aulas** y **Horarios → Armado automático** en el portal (30 s).

**Minutos 8–13 · Proceso transaccional: publicar un horario «todo o nada»**
- **Ejecuta:** `AUTH_MODE=eduspace python3 tools/demo/proceso_hermes.py`
- **Va diciendo mientras corre:**
  1. «Simular = la misma transacción, revertida: 288 clases antes, 288 después.»
  2. «Un choque se rechaza con 409; nada a medias.»
  3. «Publicar guarda la versión **y** el evento en la misma transacción.»
- **Abre el código:** `HorarioGeneradorServiceImpl.java:144` (rollback), `PublicacionHorarioServiceImpl.java:38`, `OutboxRecorder.java` (guía: `03-ensenanza-de-codigo.md` §1.1–1.5).
- **Cierra el círculo:** en Mailpit/portal, el aviso de horario publicado a docentes y familias.

**Minutos 13–15 · Cuentas**
- Muestra **Avisos y cuentas → Cuentas de acceso** (usuario nombre.apellido, clave = DNI, cambio obligatorio).

---

## 15–24 min · ANDRE — Matrícula, asistencia, tareas
**Minutos 15–17 · Qué hace su parte**
- Matrícula = dueña de la relación alumno↔sección; asistencia y tareas dependen de sus eventos (réplica `matriculas_local`).
- Muestra **Matrícula → Cupos** y **Matrículas** (estados).

**Minutos 17–22 · Proceso transaccional: matrícula con cupo atómico, pago y compensación**
- **Ejecuta:** `AUTH_MODE=eduspace python3 tools/demo/proceso_andre.py`
- **Dice:**
  1. «TX1: pre-matrícula y cupo en una transacción; el cupo es un `UPDATE … WHERE ocupados < capacidad`.»
  2. «Pago rechazado: no se cobra y la reserva sigue.»
  3. «Pago aprobado: la matrícula queda POR_APROBAR (sin pensiones aún); repetir el pago no cobra dos veces.»
  3b. «El administrador aprueba: MATRICULADO, 10 pensiones por evento, cuentas y correo con el acceso. Secretaría recibe 403.»
  4. «Compensación: al retirar, el cupo vuelve y se anulan las cuotas.»
- **Abre el código:** `CupoSeccionRepository.java:14`, `MatriculaService.java:166` y `:217`, `VencimientoJob.java` (§2.1–2.5).
- **Evidencia:** Kafka UI → tópicos `matricula.creada`, `pago.confirmado`, `matricula.confirmada`, `matricula.retirada`.

**Minutos 22–24 · Asistencia y tareas**
- Muestra el curso del docente (pestañas Tareas/Asistencia) y la hoja de asistencia (una sola notificación por falta).

---

## 24–33 min · WITMER — Pago, exámenes, calificaciones
**Minutos 24–26 · Qué hace su parte**
- Pago = dueño del dinero (conceptos, tarifas, cuotas, caja, comprobantes, mora); exámenes con tiempo del servidor; calificaciones = fuente oficial de notas y boletín PDF.
- Muestra **Finanzas → Resumen** y **Cobranza**.

**Minutos 26–31 · Proceso transaccional: cobro idempotente y su anulación**
- **Ejecuta:** `AUTH_MODE=eduspace python3 tools/demo/proceso_witmer.py`
- **Dice:**
  1. «Cobro = pago + saldos + comprobante + evento, en una transacción.»
  2. «Mismo `idempotencyKey`: mismo pago y mismo comprobante; el saldo baja una sola vez.»
  3. «Sobrepago: 409 y el saldo intacto.»
  4. «Anular: se revierte el saldo y se anula el comprobante; no se anula en caja cerrada.»
- **Abre el código:** `PagoService.java:37`, `ComprobanteService.java:39`, `AnulacionService.java:32` (§3.1–3.7).

**Minutos 31–33 · Exámenes y notas**
- Muestra el examen con reloj del servidor y la libreta/boletín PDF.

---

## 33–40 min · Todos — El proceso integrado en vivo
- **Navegador (sitio público):** `/matricula-en-linea` → elegir **2° de Primaria** → datos → «Pagar la matrícula» → tarjeta `4000 0000 0000 0002` (rechazo) → `4242 4242 4242 4242` (éxito).
  - **Quién narra:** Andre (cupo y confirmación), Witmer (cobro y pensiones), Hermes (cuentas, horario y correo).
- **Mailpit:** abrir el correo con la **constancia PDF** (finanzas, cuenta, horario).
- **Portal (admin):** *Matrícula → Matrículas* muestra la nueva; *Cuentas de acceso* muestra las cuentas creadas.
- **Seguridad en 1 min:** rutas públicas con límite propio por IP, token por trámite, `/internal` con token de servicio.
- **Métricas en 1 min:** latencias (10–70 ms), lag 0, 280+65 pruebas, auditoría de 130 pantallas (`docs/sistema/pruebas-metricas.md`).
- **Si preguntan por resiliencia:** reiniciar `auth-ms` con sesión abierta y mostrar que el token sigue valiendo; apagar un servicio y mostrar la franja «No pudimos conectar con el sistema».

## 40–45 min · Todos — Limitaciones y futuro
- Pasarela de pago simulada (punto de integración listo), correo real = configurar SMTP, PROD no levantado, pensiones prorrateadas al matricular a mitad de año, pruebas de carga pendientes (`docs/sistema/limitaciones.md`).
- Agradecer y abrir preguntas.

---

## Preguntas de defensa (ensayarlas)
| Pregunta | Quién responde | Respuesta corta |
|---|---|---|
| ¿Qué es el outbox y por qué? | Hermes | Guardar dato y evento en la misma transacción; un relé publica; no se pierde ni se inventa |
| ¿Cómo evitan vender la misma vacante dos veces? | Andre | `UPDATE … WHERE ocupados < capacidad`: atómico, devuelve 0 si no hay cupo |
| ¿Qué pasa si el pago llega dos veces? | Andre / Witmer | Idempotencia: `idempotency_key` en pago y `eventos_procesados` + estado en matrícula |
| ¿Por qué no una transacción global (2PC)? | Todos | Bases por servicio y acoplamiento; saga coreografiada con compensaciones |
| ¿Cómo se anula un cobro? | Witmer | Otra transacción: revierte saldos, anula comprobante, evento `pago.anulado`; no en caja cerrada |
| ¿Cómo saben quién es el usuario en cada servicio? | Hermes | Claim `usuarioId` del token; endpoints internos lo traducen a docente/alumno/apoderado |
| ¿Qué pasa si Kafka cae? | Todos | Los eventos esperan en `outbox_eventos`; el relé reintenta |
| ¿Qué pasa si un servicio cae? | Todos | Circuit breaker/fallback, 503 controlado en la web con franja de aviso y reintento |
| ¿Cómo prueban todo esto? | Todos | Pruebas con PostgreSQL real, 6 escenarios e2e, scripts de demo y auditoría de pantallas |
| ¿Qué falta? | Todos | Ver limitaciones: PROD, pasarela real, carga, cobertura |

## Reparto de la «parte de código» (cada uno enseña el suyo)
| Integrante | Muestra en el editor (máx. 3 archivos) |
|---|---|
| Hermes | `HorarioGeneradorServiceImpl` (rollback), `PublicacionHorarioServiceImpl`, `OutboxRecorder`/`OutboxRelay` |
| Andre | `CupoSeccionRepository.reservar`, `MatriculaService` (TX1 y confirmación), `VencimientoJob` |
| Witmer | `PagoService.registrarPago`, `ComprobanteService`, `AnulacionService` |

## Lista de verificación final (5 min antes)
- [ ] 10 servicios `UP` y registrados en Eureka.
- [ ] `proceso_hermes.py`, `proceso_andre.py` y `proceso_witmer.py` ensayados una vez hoy.
- [ ] Mailpit vacío o con el correo de ensayo identificado.
- [ ] Caja del cajero **abierta** (la usa la demo de Witmer).
- [ ] Capturas de respaldo guardadas.
- [ ] Cada uno sabe a quién le pasa la palabra y qué comando ejecuta.
