# Diapositivas de EduSpace — especificación para un generador de presentaciones

> **Cómo usar este archivo:** copia todo lo que está debajo de la línea «INSTRUCCIONES PARA EL GENERADOR» en tu generador (o pásalo como prompt/archivo).
> Cada `## Diapositiva N` tiene: **Título**, **Contenido** (viñetas ya redactadas), **Visual** (qué dibujar) y **Notas del orador**.

---

## INSTRUCCIONES PARA EL GENERADOR

**Crea una presentación de 26 diapositivas en español (formato 16:9) sobre «EduSpace: sistema de gestión escolar distribuido», para una defensa técnica universitaria de ~45 minutos (curso Desarrollo de Aplicaciones Distribuidas, UPeU 2026-2).**

Reglas de diseño:
- **Estilo:** limpio y técnico, fondo claro (#F6F8FC), títulos en azul oscuro (#1F3A87), acento naranja (#E8890C) para lo importante y verde (#0B5D41) para «correcto/éxito», rojo (#B42318) para «falla/compensación».
- **Tipografía:** sans-serif moderna (Inter o similar); título 36 pt, cuerpo 22–24 pt, código 16–18 pt en monoespaciada.
- **Densidad:** máximo **5 viñetas** por diapositiva y **8 palabras por viñeta**; el detalle va en las notas del orador, no en la diapositiva.
- **Diagramas:** rehacerlos como esquemas vectoriales limpios (cajas redondeadas y flechas), nunca como texto. Usar la descripción de «Visual» de cada diapositiva.
- **Código:** bloques cortos (máx. 8 líneas), con las líneas clave resaltadas; fuente monoespaciada sobre fondo oscuro (#14213D) con texto claro.
- **Consistencia:** cada integrante usa un **color de cinta lateral**: Hermes azul (#1F3A87), Andre naranja (#E8890C), Witmer verde (#0B5D41); las diapositivas comunes, gris (#4B5875).
- **Pie de diapositiva:** número y «EduSpace · DIST 2026-2». **Incluir notas del orador** en cada diapositiva con el texto que se indica.
- **Sin imágenes de stock.** Usar iconos lineales simples (base de datos, nube, candado, mensaje, reloj).
- **Cierre de cada bloque de integrante:** una diapositiva «Qué aprendimos / frase clave».

---

## Diapositiva 1 · Portada
- **Título:** EduSpace
- **Subtítulo:** Sistema de gestión escolar distribuido
- **Contenido:** Colegio Horizonte del Valle · DIST 2026-2 · UPeU · Hermes · Andre Fabricio · Witmer
- **Visual:** logotipo «E» en un cuadrado azul, líneas de red suaves de fondo; sin foto.
- **Notas:** «Somos tres integrantes; cada uno defenderá sus servicios y un proceso transaccional.»

## Diapositiva 2 · El problema
- **Título:** El colegio vive en hojas sueltas
- **Contenido:** Matrícula, asistencia, notas, cobros y horarios por separado · Datos duplicados y desfasados · Las familias se enteran tarde · Cada área tiene su propia verdad
- **Visual:** cinco cajas desconectadas (iconos) con flechas rotas entre ellas.
- **Notas:** «Un mismo alumno existe en cinco listas distintas; cuando una cambia, las otras no se enteran.»

## Diapositiva 3 · La solución
- **Título:** Una verdad por dato, eventos para el resto
- **Contenido:** 10 microservicios con base propia · API síncrona + eventos Kafka · Gateway único · Portal por rol + sitio público
- **Visual:** las mismas cinco cajas ahora conectadas por una línea central (Kafka) con un Gateway arriba.
- **Notas:** «Ningún servicio lee la base de otro: lo que necesita le llega por evento o por consulta interna.»

## Diapositiva 4 · Cifras del sistema
- **Título:** EduSpace en cifras
- **Contenido:** 10 servicios + Config, Eureka y Gateway · ~316 endpoints · 25 tópicos Kafka · ~280 pruebas backend + 65 frontend · 130 pantallas auditadas
- **Visual:** 5 tarjetas con número grande y etiqueta pequeña.
- **Notas:** «Cifras medidas el 29 de octubre; el detalle está en la documentación de métricas.»

## Diapositiva 5 · Arquitectura
- **Título:** Arquitectura distribuida
- **Visual (principal):** Diagrama de bloques: **Navegador → Gateway (:18080, JWT, límites) → 10 servicios**; a la izquierda **Keycloak/auth-ms**; abajo **PostgreSQL por servicio**; al centro **Kafka**; a la derecha **MinIO**; arriba **Config Server** y **Eureka**.
- **Contenido (máx. 3 viñetas):** Gateway valida JWT y limita peticiones · Eureka descubre, Config centraliza · Kafka comunica los cambios
- **Notas:** «Cada flecha tiene un contrato: HTTP interno con token de servicio o evento con sobre estándar.»

## Diapositiva 6 · Reparto del equipo
- **Título:** Quién defiende qué
- **Visual:** tabla de 3 columnas con la cinta de color de cada integrante.
  - **Hermes:** planificación · alumnos · notificaciones (+ auth)
  - **Andre Fabricio:** matrícula · asistencia · tareas
  - **Witmer:** pago · exámenes · calificaciones
- **Contenido (pie):** Infraestructura, seguridad y observabilidad: de todos.
- **Notas:** «Cada uno mostrará un proceso transaccional de su parte.»

## Diapositiva 7 · Las tres ideas de consistencia
- **Título:** Cómo mantenemos la consistencia sin transacción global
- **Contenido:** **Outbox:** dato + evento en la misma transacción · **Idempotencia:** repetir no duplica · **Compensación:** deshacer con otro paso
- **Visual:** tres iconos grandes (caja de salida, doble check, flecha de deshacer) con una frase cada uno.
- **Notas:** «Es la clase de consistencia distribuida: transacciones locales, eventos y compensaciones.»

---

## Diapositiva 8 · [Hermes] Su parte
- **Cinta:** azul · **Título:** Hermes · planificación, alumnos, notificaciones
- **Contenido:** Planificación: año, secciones, malla, docentes, horarios · Alumnos: expediente, apoderados, documentos · Notificaciones: bandeja, correo, plantillas · Auth: cuentas nombre.apellido
- **Visual:** capturas del **Panel de aulas** y de **Armado automático** (lado a lado).
- **Notas:** «Planificación es la fuente de verdad que casi todos consultan.»

## Diapositiva 9 · [Hermes] El proceso
- **Título:** Publicar un horario «todo o nada»
- **Visual (principal):** flujo: **Simular (rollback)** → **Generar sin cruces** → **Validar** → **Publicar** → **(TX: versión + evento)** → **Kafka** → **Avisos**; la caja «Simular» con una etiqueta roja «nada se guarda».
- **Contenido:** Simular = misma transacción revertida · Choque → 409, nada a medias · Publicar = versión + evento juntos
- **Notas:** «Antes de publicar se valida; al publicar, la versión y el evento entran en una sola transacción.»

## Diapositiva 10 · [Hermes] Código
- **Título:** El código clave
- **Visual:** dos bloques de código lado a lado:
  1. `HorarioGeneradorServiceImpl` → `if (simular) { TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); }`
  2. `OutboxRecorder` → `@TransactionalEventListener(phase = BEFORE_COMMIT) … INSERT INTO outbox_eventos …`
- **Notas:** «Mostrar en el editor: línea 144 del generador y el recorder. Frase clave: la simulación es la misma transacción, revertida.»

## Diapositiva 11 · [Hermes] Demostración
- **Título:** Demo en vivo
- **Contenido:** `python3 tools/demo/proceso_hermes.py` · 288 clases antes y después de simular · Duplicado → HTTP 409 · Versión publicada + aviso
- **Visual:** captura de la salida del script con tres líneas resaltadas.
- **Notas:** «Ejecutar el script; después mostrar el aviso en el portal.»

## Diapositiva 12 · [Hermes] Resultado
- **Título:** Qué aprendimos
- **Contenido:** El rollback permite simular con seguridad · El outbox evita perder avisos · Los consumidores deben ser idempotentes
- **Visual:** frase destacada: «Lo que veo es lo que se guardaría.»
- **Notas:** «Medidas: 12 secciones, 288 clases, 0 cruces, 0,3–0,6 s.»

---

## Diapositiva 13 · [Andre] Su parte
- **Cinta:** naranja · **Título:** Andre · matrícula, asistencia, tareas
- **Contenido:** Matrícula: campañas, cupos, estados, traslado, retiro · Asistencia: sesiones, justificaciones, reportes · Tareas: entregas con archivos, calificación 0–20 · Réplica local `matriculas_local`
- **Visual:** capturas de **Cupos** y del **curso del docente** (pestaña Tareas).
- **Notas:** «Matrícula es la dueña de la relación alumno↔sección.»

## Diapositiva 14 · [Andre] El proceso
- **Título:** Matrícula: cupo atómico, pago y compensación
- **Visual (principal):** diagrama de secuencia con tres carriles (**matrícula · Kafka · pago**): TX1 pre-matrícula+cupo → `matricula.creada` → cuota → cobro → `pago.confirmado` → POR_APROBAR → **aprobación del administrador** → MATRICULADO → `matricula.confirmada` → pensiones. Flechas rojas punteadas de **compensación**: vence/retiro → `matricula.anulada|retirada` → cuotas anuladas, cupo libre.
- **Contenido:** Saga coreografiada · Cada paso es una transacción local · Compensación por vencimiento, anulación o retiro
- **Notas:** «No hay transacción global: cada servicio reacciona a eventos.»

## Diapositiva 15 · [Andre] Código
- **Título:** El código clave
- **Visual:** 
  1. `CupoSeccionRepository`: `UPDATE … SET ocupados = ocupados + 1 WHERE ocupados < capacidad` (resaltar `WHERE`).
  2. `EventoOutboxService`: `@Transactional(propagation = Propagation.MANDATORY)`.
- **Notas:** «Una sola sentencia comprueba y reserva: no hay carrera por la última vacante. MANDATORY obliga a que el evento viaje dentro de la transacción.»

## Diapositiva 16 · [Andre] Demostración
- **Título:** Demo en vivo
- **Contenido:** `python3 tools/demo/proceso_andre.py` · Rechazo → 402, reserva intacta · Pago → POR_APROBAR; el admin aprueba → MATRICULADO + 10 pensiones · Retiro → el cupo vuelve
- **Visual:** captura de la salida + captura de Kafka UI con los tópicos.
- **Notas:** «Mostrar los cupos antes/después en la salida del script.»

## Diapositiva 17 · [Andre] Resultado
- **Título:** Qué aprendimos
- **Contenido:** La concurrencia se resuelve en la base, no en el código · Idempotencia: el pago repetido no duplica · Lo que no se paga, se libera
- **Visual:** frase destacada: «El cupo es una sentencia, no una esperanza.»
- **Notas:** «Pruebas: último cupo con 2 hilos, pago repetido, vencimiento, outbox con Kafka caído.»

---

## Diapositiva 18 · [Witmer] Su parte
- **Cinta:** verde · **Título:** Witmer · pago, exámenes, calificaciones
- **Contenido:** Pago: conceptos, tarifas, cuotas, caja, comprobantes, mora · Exámenes: intento con tiempo del servidor · Calificaciones: criterios, promedios, boletín PDF
- **Visual:** capturas de **Finanzas → Resumen** y de la **libreta/boletín**.
- **Notas:** «Pago es el dueño del dinero: aquí un error cuesta caro.»

## Diapositiva 19 · [Witmer] El proceso
- **Título:** Cobro idempotente con comprobante y evento
- **Visual (principal):** flujo: **POST /pagos + idempotencyKey** → decisión «¿ya existe?» (sí → mismo pago) → recuadro **UNA transacción** con: validar · pago+detalle · saldos (@Version) · comprobante (FOR UPDATE) · outbox → **Kafka**. Abajo, flecha roja: **Anular** → revierte saldos + comprobante anulado + `pago.anulado`.
- **Contenido:** Reintento = mismo pago · Comprobante sin huecos · Anulación como compensación
- **Notas:** «Una transacción: pago, saldos, comprobante y evento.»

## Diapositiva 20 · [Witmer] Código
- **Título:** El código clave
- **Visual:**
  1. `PagoService`: `findByIdempotencyKey(req.getIdempotencyKey())` → devolver el pago existente.
  2. `ComprobanteService`: `findFirstByTipoActivaForUpdate(tipo)` → `ultimoNumero + 1`.
  3. `AnulacionService`: `cuota.setSaldo(cuota.getSaldo().add(montoAplicado))`.
- **Notas:** «Tres ideas: idempotencia al entrar, bloqueo del correlativo, compensación que restituye saldo.»

## Diapositiva 21 · [Witmer] Demostración
- **Título:** Demo en vivo
- **Contenido:** `python3 tools/demo/proceso_witmer.py` · Mismo `idempotencyKey` → mismo comprobante · Sobrepago → 409 · Anular → saldo restituido
- **Visual:** captura de la salida con «mismo pago: True» resaltado.
- **Notas:** «Dejar la caja abierta para la demo; cerrarla desde la pantalla Caja después.»

## Diapositiva 22 · [Witmer] Resultado
- **Título:** Qué aprendimos
- **Contenido:** La clave de idempotencia protege del reintento · El bloqueo del correlativo evita duplicados · Anular no borra: compensa
- **Visual:** frase destacada: «Cobrar dos veces no es un error de red: es un error de diseño.»
- **Notas:** «Pruebas: 2 cobros simultáneos, comprobante único, anulación.»

---

## Diapositiva 23 · [Todos] El proceso integrado
- **Cinta:** gris · **Título:** Matrícula en línea, de punta a punta
- **Visual (principal):** línea de tiempo horizontal con 8 hitos y el color de quien lo resuelve: **Datos (azul) → Reserva de cupo (naranja) → Pago simulado (verde) → Aprobación del administrador (naranja) → Cuentas (azul) → Pensiones (verde) → Horario (azul) → Correo con acceso y constancia (azul)**.
- **Contenido:** Sitio público sin sesión, con límite propio · Token por trámite · Correo con constancia PDF
- **Notas:** «Demostrar en el navegador; abrir Mailpit al final.»

## Diapositiva 24 · [Todos] Seguridad y resiliencia
- **Título:** Seguridad y resiliencia
- **Contenido:** JWT (Keycloak / auth-ms) · `/internal` con token de servicio · Límite por IP en el Gateway · Clave de firma persistente en DEV · Franja «servicio caído» y vuelta al login
- **Visual:** candado y escudo simples + captura de la franja de aviso.
- **Notas:** «Preparar: reiniciar auth-ms con la sesión abierta; el mismo token sigue valiendo.»

## Diapositiva 25 · [Todos] Métricas y calidad
- **Título:** Lo medimos
- **Contenido:** Latencia mediana 10–70 ms · Horario de 288 clases en 0,3–0,6 s · Lag de Kafka 0 · 280 pruebas backend + 65 frontend · 130 pantallas auditadas
- **Visual:** tabla pequeña de latencias y un indicador verde por métrica.
- **Notas:** «Cifras de `docs/sistema/pruebas-metricas.md`; medidas en entorno de desarrollo.»

## Diapositiva 26 · [Todos] Limitaciones y cierre
- **Título:** Lo que falta y lo que sigue
- **Contenido:** Pasarela de pago real · Correo real (SMTP) · PROD y pruebas de carga · Prorrateo de pensiones · Gracias — ¿preguntas?
- **Visual:** línea con «Hoy» y «Siguiente», y al final el contacto del equipo.
- **Notas:** «Cerrar con la frase de apertura: una familia matricula desde la web y todo ocurre por transacciones locales y eventos; si algo falla, se compensa.»

---

### Apéndice A · Diapositivas de reserva (opcionales, tras la 26)
- **A1 · Modelo de datos:** tablas por servicio (de `docs/sistema/datos.md`).
- **A2 · Roles y permisos:** matriz por rol (de `docs/sistema/seguridad.md`).
- **A3 · Algoritmo de horarios:** MRV + búsqueda local con lista tabú, semilla fija.
- **A4 · Observabilidad:** Prometheus/Grafana/Loki y `X-Trace-ID`.

### Apéndice B · Tiempo por diapositiva (referencia)
1–7: 6 min · 8–12: 9 min · 13–17: 9 min · 18–22: 9 min · 23–25: 7 min · 26: 5 min (incluye preguntas).

### Apéndice C · Lista de capturas a tomar en el ensayo
Panel de aulas · Armado automático · Cupos · Curso del docente (Tareas) · Finanzas → Resumen · Libreta/boletín · Eureka · Kafka UI · Mailpit con el correo · franja «servicio caído» · salida de los tres scripts de demo.


---

## Anexo: diapositivas para la demostración rápida (10 min)

Si el tiempo es corto, sustituye los bloques por integrante por estas 8 diapositivas (guion en `05-demo-rapida.md`). Una idea por diapositiva, texto mínimo, captura de la pantalla real.

| # | Título | Contenido (máx. 4 viñetas) | Imagen |
|---|---|---|---|
| 1 | EduSpace en una frase | Colegio digital: web + app móvil; 10 microservicios; un hilo: «matricular → enseñar → evaluar → informar» | Logo + diagrama de 10 cajas |
| 2 | Proceso 1 · Nuevo alumno | Matrícula en línea · pago simulado · expediente y aprobación administrativa · correo con constancia | Captura del formulario público y de Mailpit |
| 3 | Aprobación de matrícula | El pago deja la matrícula POR_APROBAR · solo el ADMIN la aprueba (MATRICULADO) o la rechaza con motivo · si no paga, vence en 1 día · al aprobar llega el correo con usuario y clave | Pantalla Matrícula → Por aprobar |
| 4 | Proceso 2 · Profesor en clase | Hoy → pasar lista → crear y publicar tarea (app móvil) | Captura móvil: pasar lista |
| 5 | Proceso 3 · Alumna | Entrega la tarea (archivo directo a MinIO) · rinde el examen (corrección en servidor) | Capturas de tarea y examen |
| 6 | Proceso 4 · Apoderada | Asistencia, notas, boletín y pagos de su hija; avisos en tiempo real | Captura móvil: inicio de familia |
| 7 | Proceso 5 · Administración y finanzas | Cobranza, comprobantes, anulación con compensación, cuentas y restauración de claves | Captura de Finanzas |
| 8 | Qué lo hace confiable | Saga + outbox + idempotencia · JWT y roles · trazabilidad `X-Trace-ID` · pruebas y métricas | Diagrama de eventos |
