# services

Microservicios de negocio, un proyecto Spring Boot independiente por carpeta, con su propia base PostgreSQL.

| Carpeta | Puerto | Base DEV | Responsable | Estado |
|---|---|---|---|---|
| `auth-ms` | 8090 | 15440 | infra | implementado · 15 pruebas |
| `planificacion-ms` | 8091 | 15441 | Hermes | implementado · 36 pruebas |
| `alumnos-ms` | 8092 | 15442 | Hermes | implementado · 35 pruebas |
| `matricula-ms` | 8093 | 15443 | Andre | implementado · 24 pruebas |
| `asistencia-ms` | 8094 | 15444 | Andre | implementado · 21 pruebas |
| `calificaciones-ms` | 8095 | 15445 | Witmer | corregido y probado · 16 pruebas |
| `pago-ms` | 8096 | 15446 | Witmer | corregido y probado · 23 pruebas |
| `examenes-ms` | 8097 | 15447 | Witmer | corregido y probado · 21 pruebas |
| `tareas-ms` | 8098 | 15448 | Andre | implementado · 21 pruebas |
| `notificaciones-ms` | 8099 | 15449 | Hermes | implementado · 43 pruebas |

Levantar uno: `docker compose -f compose-dev.yml up -d` y luego `./mvnw spring-boot:run`, parado en su carpeta.
Todo el conjunto, con Kafka y datos de prueba: `../tools/e2e/`.

Paquetes: Hermes `pe.edu.eduspace.*`; Witmer y Andre `pe.edu.upeu.eduspace.*` (sin unificar).
