# Bitácora de avance (octubre 2026)

Resumen cronológico de lo hecho sobre `eduspace-completo/`. El detalle técnico está en `05-integracion-y-correcciones.md`.

| # | Etapa | Resultado |
|---|---|---|
| 1 | Unir los dos zips (idénticos) en `eduspace-completo/` | Proyecto único; servicios de Witmer incorporados. |
| 2 | Reorganizar al patrón pagatu | `infra/` (config, eureka, gateway), `kafka/`, `obs/`, `services/`, `sql/`, `docs/`; `mvnw`, config-repo y rutas del Gateway. |
| 3 | Revisar el repo de Andre | Un solo commit con SQL e informe: no había código. |
| 4 | Actualizar a Boot 4.1.1 / Cloud 2025.1.3 | Infra (3 proyectos) y servicios de Witmer migrados; arrancan con Flyway. |
| 5 | Comparar cada servicio con el plan | 5 desvíos graves y varios menores en Witmer (ver `05`, §2). |
| 6 | Corregir a Witmer | Eventos planos, comprobantes PDF, cuotas, identidad, pendientes persistentes, validaciones de examen. |
| 7 | Construir `matricula-ms`, `asistencia-ms`, `tareas-ms` | Completos según el informe de Andre. |
| 8 | Pruebas de integración (PostgreSQL real) | 255 pruebas en 10 servicios; encontraron 8 bugs reales. |
| 9 | Corrida de punta a punta (`tools/e2e/`) | 6 escenarios, ~150 verificaciones, todos OK; encontró 6 bugs más que los mocks no ven. |
| 10 | Consumidor de `tarea.recordatorio` en notificaciones | Plantilla `V4` + prueba. |
| 11 | Construir `auth-ms` | Login, JWT RS256/JWKS, refresh rotativo, bloqueo, ABM de usuarios; 15 pruebas; los escenarios e2e ahora usan tokens reales. |
| 12 | Front web completo, Keycloak y sitio público (oct. 26–27) | Panel por rol, login con Keycloak (PKCE), sitio público y páginas por rol. |
| 13 | Inicio, matrícula y notificaciones del ADMIN (oct. 27–28) | Inicio con estadísticas, aulas y cursos/docentes; datos para matricular; CRUD de plantillas y avisos con selector de destinatarios. |
| 14 | Armado automático de horarios (oct. 28) | Algoritmo sin cruces, malla en lote y generación por secciones. Detalle en `08-avance-2026-10-28.md`. |
| 15 | Menú por espacios de trabajo (oct. 29) | 8 entradas con pestañas, paneles de aulas y cursos, aulas disponibles. Detalle en `09-espacios-de-trabajo.md`. |
| 16 | Cuentas de personas y documentación del sistema (oct. 29) | Cuentas con usuario nombre.apellido y clave = documento, cambio obligatorio, restauración; documentación completa en `docs/sistema/` con métricas. |
| 17 | Matrícula en línea (oct. 29) | Sitio público: reserva, pago simulado, confirmación, cuentas, constancia PDF y correo con horario y finanzas. Detalle en `sistema/matricula-en-linea.md`. |
| 18 | App móvil, aprobación de matrículas y materiales (oct. 29) | App Flutter por rol (docente, alumno, apoderado) con carga por rol y servidor configurable; matrícula pagada → `POR_APROBAR` → aprobación del ADMIN → correo con el acceso; materiales de curso subidos por el docente; preparación de la exposición (`tools/demo/preparar_exposicion.py`, `docs/presentacion/05-demo-rapida.md`). |

## Estado final
- 10 servicios con base de datos + Config Server, Eureka y Gateway funcionando juntos.
- Pruebas: planificación 36 · alumnos 35 · notificaciones 43 · auth 15 · matrícula 24 · asistencia 21 · tareas 21 · pago 23 · exámenes 21 · calificaciones 16.
- Pendiente: front, PROD real y los puntos de `PENDIENTES.md`.

## Entorno
Todo lo levantado para las pruebas (aplicaciones, Kafka, MinIO, Mailpit, PostgreSQL y el emisor de tokens) se detuvo y los
contenedores `e2e-*` se eliminaron. Para volver a levantarlo: `tools/e2e/compilar.sh`, `levantar.sh`, `correr_todo.sh`, `detener.sh`.
