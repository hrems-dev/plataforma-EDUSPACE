# Módulos y funcionalidades

Cada microservicio tiene un documento detallado por responsable en [Equipo](equipo.md) (`hermes.md`, `andre.md`, `witmer.md`). Aquí, la vista por funcionalidad.

| Módulo | Servicio | Qué resuelve | Estado |
|---|---|---|---|
| Identidad | `auth-ms` / Keycloak | Login, JWT, roles, cambio de clave, bloqueo por intentos, **cuentas de personas (usuario nombre.apellido, clave = documento, cambio obligatorio, restauración)** | Completo (Keycloak en DEV; las cuentas del colegio exigen el modo `auth-ms`) |
| Año escolar | planificacion | Año activo único, periodos, feriados | Completo |
| Estructura | planificacion | Niveles, grados, **secciones, aulas, tutor**, capacidad | Completo |
| Currículo | planificacion | Cursos, **malla por grado** (también en lote), horas semanales | Completo |
| Docentes | planificacion | Ficha, especialidad, asignación docente–curso–sección | Completo |
| Horarios | planificacion | Bloques, clases sin choques, validación, publicación versionada, **armado automático**, aulas disponibles | Completo |
| Expediente | alumnos | Alumno, apoderados (principal, responsable de pago), documentos, salud, historial, importación CSV | Completo |
| Matrícula | matricula | Campañas, cupos atómicos, pre-matrícula → matrícula, traslado, retiro, anulación, ratificación, requisitos, vencimiento | Completo |
| Asistencia | asistencia | Sesión por día o por curso, registro masivo, tardanzas, justificaciones con archivo, correcciones auditadas, reportes y alertas | Completo |
| Tareas | tareas | Publicación con adjuntos, entregas con versiones, calificación 0–20, pendientes, recordatorios | Completo |
| **Materiales del curso** | tareas + notificaciones | Cada docente sube **archivos** (PDF, Word, PowerPoint, Excel, imágenes, ZIP, texto; hasta 10 MB, directo a MinIO con URL firmada) o **enlaces** de sus cursos; alumnos y apoderados de la sección los ven y descargan; aviso `material.publicado`. Web (*Mis cursos → curso → Materiales*) y app móvil | Completo |
| Exámenes | examenes | Banco de preguntas, examen con ventana y duración, intento con tiempo del servidor, calificación automática y revisión | Completo (falta revisión abierta en la web) |
| Notas | calificaciones | Criterios con pesos (100 %), promedios ponderados, cierre y reapertura, boletín PDF, libretas, resultado anual | Completo |
| Cobranza | pago | Conceptos, tarifas, descuentos, cuotas, caja, pagos idempotentes, comprobantes PDF, mora, morosos, alertas | Completo (pasarela simulada) |
| Avisos | notificaciones | Bandeja, plantillas, preferencias, push (bitácora), envío manual con destinatarios, administración de avisos | Completo (sin FCM/SMS) |
| **Matrícula en línea** | matricula + alumnos + pago + auth + notificaciones | Desde el sitio público: reserva de vacante, pago (simulado), **aprobación del administrador**, y entonces cuentas, constancia PDF y correo con el acceso, horario y finanzas | Completo (pasarela simulada) |
| Portal web | frontend | 6 roles + sitio público | Ver [Frontend](frontend.md) |

## Funcionalidades destacadas

### Armado automático de horarios
Pasos en pantalla: (1) cursos y horas por grado en lote; (2) qué secciones están listas y qué les falta; (3) **simular** o **generar**, con asignación de docentes por especialidad opcional.
Resultado medido en Primaria: 12 secciones, 288 clases, 0 cruces de docente, aula ni sección, en 0,3–0,6 s.

### Panel de aulas
Lista vertical de secciones con ocupación. Al elegir una: aula, tutor, matriculados, pre-matrícula, vacantes, asistencia promedio y alumnos con faltas, cursos y docentes
(asignar/cambiar/quitar), alumnos con *Matricular* y *Trasladar*, editar o crear sección.

### Aulas disponibles
Mapa día × bloque con cuántas aulas están libres, quién ocupa cada una y la semana completa de cada aula. En el editor de horario los docentes y aulas ocupados se marcan y bloquean.

### Avisos con destinatarios
Docentes, familias de una sección, alumnos concretos o usuarios por número; plantillas editables (las 15 del sistema no se borran); administración de los avisos enviados.

### Sitio público
Inicio, nosotros, niveles, admisión, noticias y contacto, sin sesión.
