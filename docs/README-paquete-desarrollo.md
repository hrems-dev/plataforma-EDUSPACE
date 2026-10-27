# EduSpace — Paquete de desarrollo

Material para desarrollar el proyecto (arquitectura pagatu). Describe **qué debe hacer** cada servicio y cada archivo; no incluye código.

| Carpeta | Contenido |
|---|---|
| `00-general/` | Informe general: mapa de los 10 servicios, flujos, **33 hallazgos de huecos y cuellos de botella**, estándares |
| `01-infraestructura-inicial/` | Config Server, config-repo, Eureka, Gateway, auth-ms, Kafka, MinIO, observabilidad + `sql/auth-ms` |
| `02-hermes-planificacion-alumnos-notificaciones/` | Informe ya entregado + **ADENDA** de correcciones + SQL (incluye `V2` de notificaciones) |
| `03-andre-matricula-asistencia-tareas/` | Informe de Andre Fabricio + SQL de sus 3 servicios |
| `04-witmer-pago-examenes-calificaciones/` | Informe de Witmer + SQL de sus 3 servicios |
| `99-anexos-historicos/` | Documento inicial de distribución (reemplazado por este paquete) |

## Orden de lectura
1. `00-general/00-informe-general-todos-los-servicios.md`
2. `01-infraestructura-inicial/informe-infraestructura-inicial.md`
3. El informe propio (y la adenda, en el caso de Hermes)

## Scripts SQL
Cada servicio trae su `V1__*.sql` listo para Flyway (`src/main/resources/db/migration/`). Los 10 scripts V1 y la V2 de notificaciones se probaron contra PostgreSQL 16 sin errores.
