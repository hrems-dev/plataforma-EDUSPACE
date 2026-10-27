# EduSpace — proyecto unificado

Sistema de gestión escolar en **10 microservicios + infraestructura** (patrón pagatu, Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3,
PostgreSQL 16 + Flyway con `ddl-auto: validate`, Kafka, MinIO). Todo está implementado y probado **por consola** (API REST + Swagger);
el front se agrega después.

| Servicio | Puerto | Base (DEV) | Responsable | Qué hace |
|---|---|---|---|---|
| `planificacion-ms` | 8091 | `planificacion_db` · 15441 | Hermes | Año académico, periodos, niveles/grados, secciones, aulas, cursos y malla, docentes, asignaciones, horario con validación de choques y publicación. |
| `auth-ms` | 8090 | `auth_db` · 15440 | Hermes (infra) | Login, JWT RS256 + JWKS, refresh con rotación, bloqueo por intentos, cambio de clave, administración de usuarios y roles. |
| `alumnos-ms` | 8092 | `alumnos_db` · 15442 | Hermes | Expediente: alumno, apoderados, documentos en MinIO (URL firmada), ficha de salud, historial de estado. |
| `matricula-ms` | 8093 | `matricula_db` · 15443 | Andre | Campañas, cupos atómicos, pre-matrícula → matrícula (por pago), traslado, retiro, ratificación, requisitos, vencimiento. |
| `asistencia-ms` | 8094 | `asistencia_db` · 15444 | Andre | Sesiones por día/curso, registro masivo, justificaciones con archivo, correcciones auditadas, reportes. |
| `calificaciones-ms` | 8095 | `calificaciones_db` · 15445 | Witmer | Criterios y pesos, notas (manuales y por eventos), promedios, cierre/reapertura de periodo, boletín PDF, libretas, resultado anual. |
| `pago-ms` | 8096 | `pago_db` · 15446 | Witmer | Conceptos, tarifas, descuentos, cuotas, caja, pagos idempotentes, comprobantes PDF, mora, morosos. |
| `examenes-ms` | 8097 | `examenes_db` · 15447 | Witmer | Banco de preguntas, exámenes, intentos con tiempo del servidor, calificación automática y revisión. |
| `tareas-ms` | 8098 | `tareas_db` · 15448 | Andre | Tareas, entregas con archivos en MinIO, calificación (0–20), pendientes, recordatorios. |
| `notificaciones-ms` | 8099 | `notificaciones_db` · 15449 | Hermes | Consume 15 tópicos y crea notificaciones IN_APP / EMAIL / PUSH. |

`docs/` trae los informes del paquete de desarrollo, `docs/05-integracion-y-correcciones.md` (contratos, correcciones y decisiones
de esta etapa) y `docs/06-bitacora-de-avance.md` (cronología), `docs/07-verificacion-plan-y-pagatu.md` (verificación contra el plan y pagatu). `sql/` trae los V1 de referencia y `00_crear_bases.sql`.

## Estructura (patrón pagatu)

```
eduspace-completo/
├── infra/
│   ├── eduspace-config/    # Config Server (:18888) + config-repo/ (<servicio>-dev.yml / -prod.yml)
│   ├── eduspace-eureka/    # Registro de servicios (:18761)
│   └── eduspace-gateway/   # Punto único de entrada (:18080), 10 rutas lb:// (planificacion … tareas)
├── kafka/                  # Kafka + Kafka UI + MinIO (compose-dev.yml / compose.yml)
├── obs/                    # Prometheus + Loki + Promtail + Grafana
├── services/               # un proyecto Spring Boot por servicio: pom, mvnw, Dockerfile, compose(-dev).yml, src
├── tools/e2e/              # levantar el entorno completo y correr 6 escenarios de punta a punta por consola
├── sql/                    # V1 de auth-ms y 00_crear_bases.sql (el resto de V1 vive en cada servicio)
└── docs/                   # informes del paquete de desarrollo + 05-integracion-y-correcciones.md
```

Todos los servicios tienen código. Ver `PENDIENTES.md`.

## Cómo correrlo (DEV)

```bash
# 1. Infraestructura: bases de datos, Kafka, MinIO, Mailpit
docker compose -f kafka/compose-dev.yml up -d
for s in planificacion-ms alumnos-ms notificaciones-ms matricula-ms asistencia-ms tareas-ms calificaciones-ms pago-ms examenes-ms; do docker compose -f services/$s/compose-dev.yml up -d; done   # notificaciones también levanta Mailpit

# 2. Cada servicio (con Config Server + Eureka + auth-ms del equipo ya arriba, es lo normal):
cd services/planificacion-ms && ./mvnw spring-boot:run

# Sin Config Server / Eureka / auth-ms (modo aislado): lee config-repo/ directamente
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.name=application,planificacion-ms --spring.config.additional-location=file:../../infra/eduspace-config/config-repo/ --spring.cloud.config.enabled=false --eureka.client.enabled=false"
# notificaciones-ms, además: --app.clients.alumnos.url=http://localhost:8092   (llamar a alumnos-ms sin Eureka)
```

Swagger: `http://localhost:8091/swagger-ui.html` (y 8092, 8099). Correos de prueba: Mailpit en `http://localhost:18025`.
Kafka UI: `http://localhost:18085`. Los endpoints `/internal/**` **no** se enrutan por el Gateway.

## Pruebas

```bash
cd services/<servicio> && ./mvnw test       # necesita Docker o Podman (Testcontainers levanta PostgreSQL 16 real)
```

| Servicio | Pruebas | Qué cubren (todas con PostgreSQL real; servicios vecinos y Kafka simulados) |
|---|---|---|
| `auth-ms` | 15 | semillas con BCrypt, JWT con el contrato y JWKS sin parte privada, mismo error para usuario/clave, bloqueo 5 intentos/423, refresh con rotación y detección de reutilización, cambio de clave, ABM de usuarios, último ADMIN protegido, sin claves el servicio no arranca |
| `planificacion-ms` | 36 | esquema Flyway, API completa con JWT y roles, evento solo tras commit, `/internal/**` |
| `alumnos-ms` | 35 | esquema Flyway, API completa, privacidad ("solo lo propio"), subida de documentos, outbox |
| `notificaciones-ms` | 43 | los 14 tópicos → destinatarios, idempotencia, caída de alumnos-ms y circuito abierto, reintentos, ShedLock |
| `matricula-ms` | 24 | último cupo con 2 hilos, un alumno una matrícula viva, expediente/campaña, pago repetido, traslado a sección llena, vencimiento, outbox con Kafka caído, ratificación masiva, seguridad |
| `asistencia-ms` | 21 | docente no asignado, feriado/futura, guardar dos veces sin duplicar faltas, justificación que convierte solo el rango, traslado, cierre nocturno, planificación caída |
| `tareas-ms` | 21 | otra sección no entrega, plazo con/sin tardía, reenvío y versión, nota 0–20 con `periodoId`, tipos/tamaños, recordatorio único, cierre automático |
| `pago-ms` | 23 | reparto de pensión, año calendario, sin tarifa → alerta, comprobante único (PDF) y leer no emite, 2 cobros simultáneos, anulación, mora lineal, avisos |
| `examenes-ms` | 21 | alumno sin respuesta correcta, un solo intento en curso, upsert, calificación, abiertas, cierre de intentos, IDOR, edición y publicación |
| `calificaciones-ms` | 16 | pesos 100 %, promedio ponderado, cierre exigente, reapertura auditada, notas pendientes persistentes, boletín PDF versionado, libretas por dueño |

Total: **255 pruebas en verde**. Con Podman: `systemctl --user start podman.socket` y
`export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true`.

### Corrida de punta a punta (por consola)

```bash
tools/e2e/compilar.sh      # jars de infra + 9 servicios
tools/e2e/levantar.sh      # Kafka, MinIO, Mailpit, 10 PostgreSQL, Config, Eureka, auth-ms (:8090), servicios, Gateway
# esperar ~90 s a que todo esté UP y luego:
tools/e2e/correr_todo.sh   # 6 escenarios; imprime cada llamada (OK/ERR) y termina con "TODOS LOS ESCENARIOS OK"
tools/e2e/detener.sh
```

Necesita unos 9 GB de RAM libres (13 JVM de 256 MB). Ver `tools/e2e/README.md`.

## Decisiones que se apartan del texto del informe (y por qué)

1. **`versionHorario` en `horario.publicado`.** El informe pide un campo `version` con la versión del horario, pero el sobre
   estándar de la adenda (A1) ya usa `version` para la versión del contrato. Se envía como `versionHorario`;
   `notificaciones-ms` lo lee con ese nombre. Los demás integrantes deben usarlo igual.
2. **Notificación PENDIENTE sin destinatario.** Si `alumnos-ms` no responde, no se sabe a quién notificar y `usuario_id` es
   `NOT NULL`. Se guarda una fila marcadora con `usuario_id = 0` (nunca aparece en la bandeja de nadie) con lo necesario para
   re-resolver; `ReintentoScheduler` la convierte en notificaciones reales cuando `alumnos-ms` vuelve.
3. **`V3__plantillas_horario_y_correo.sql` (nuevo, aditivo).** El informe pide avisar a docentes con `horario.publicado` pero
   V1/V2 no traían esa plantilla; además no había ninguna plantilla EMAIL. V3 agrega `HORARIO_PUBLICADO` (IN_APP) y 5 plantillas
   EMAIL (pagos, matrícula confirmada, boletín). V1 y V2 se usan tal cual.
4. **Eventos con `eventId` y sobre estándar** (adenda A1) en los 5 eventos producidos; el consumidor descarta repetidos por
   `eventId` (`eventos_procesados`) y, sin él, por la restricción única de la base.
5. **Código de alumno** `ALaaaa-NNNNN` (año + correlativo), generado bajo un candado de transacción de PostgreSQL para que dos altas simultáneas no choquen.
6. **Endpoints `/internal/**` de las adendas A2 y A3** implementados completos (docentes por usuario, asignaciones, secciones,
   periodos, cursos, expediente completo, hijos de un apoderado, lote, responsable de pago…).

## Límites conocidos

- **"Docente tutor" en la ficha de salud.** Saber quién es tutor de qué alumno exige datos de `planificacion-ms` y `matricula-ms`.
  Mientras no exista esa consulta, `app.salud.docentes-pueden-leer` (por defecto `false`; solo DEV lo activa) decide si cualquier DOCENTE lee la ficha.
  Cada consulta queda en el log con el usuario.
- **Envío manual por rol.** `POST /enviar` acepta una lista de `usuarioIds` y los canales IN_APP / PUSH. Enviar "a todos los de un
  rol" necesita que `auth-ms` liste usuarios por rol (no está en su especificación), y el correo necesita direcciones que solo
  conocen alumnos/apoderados.
- **Sin cuenta no hay bandeja.** Un apoderado sin `usuario_id` (cuenta en `auth-ms`) no recibe notificaciones: la tabla exige
  destinatario. En el MVP las cuentas se crean a mano.
- **Push** es un `LogPushSender` (MVP, como pide el informe); FCM queda para la fase 2.
- **Tópicos de otros servicios.** Los productores (`matricula`, `pago`, `asistencia`, `tareas`, …) crean sus tópicos al arrancar; hasta
  entonces `notificaciones-ms` solo avisa `UNKNOWN_TOPIC_OR_PARTITION` en el log y sigue funcionando.
- **MinIO.** Las imágenes oficiales ya no se publican en Docker Hub ni Quay; `kafka/compose-dev.yml` usa `cgr.dev/chainguard/minio`.
- **PROD sin probar.** `application-prod.yml` (sin `optional:`, `fail-fast`) y los `compose.yml`/`Dockerfile` están escritos pero
  no se levantaron en una red `eduspace-prod-net` real.
