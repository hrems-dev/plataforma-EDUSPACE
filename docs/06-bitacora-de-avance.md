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

## Estado final
- 10 servicios con base de datos + Config Server, Eureka y Gateway funcionando juntos.
- Pruebas: planificación 36 · alumnos 35 · notificaciones 43 · auth 15 · matrícula 24 · asistencia 21 · tareas 21 · pago 23 · exámenes 21 · calificaciones 16.
- Pendiente: front, PROD real y los puntos de `PENDIENTES.md`.

## Entorno
Todo lo levantado para las pruebas (aplicaciones, Kafka, MinIO, Mailpit, PostgreSQL y el emisor de tokens) se detuvo y los
contenedores `e2e-*` se eliminaron. Para volver a levantarlo: `tools/e2e/compilar.sh`, `levantar.sh`, `correr_todo.sh`, `detener.sh`.
