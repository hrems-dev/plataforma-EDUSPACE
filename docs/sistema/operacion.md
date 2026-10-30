# Operación y problemas frecuentes

## Ambientes
| | DEV | PROD |
|---|---|---|
| Config | `config-repo/<servicio>-dev.yml` | `-prod.yml` (secretos por variable de entorno) |
| Emisor de tokens | Keycloak (`JWK_SET_URI` por defecto) | `auth-ms` (claves RSA persistentes) |
| Token de servicio | valor de desarrollo | **obligatorio** (`SERVICE_TOKEN`), sin valor por defecto |
| Red | puertos locales | `eduspace-prod-net`; solo el Gateway se publica (`compose.yml`) |
| Actuator | `health,info,prometheus` | igual; el Gateway deja pasar solo `health` e `info` |

Variables relevantes: `JWK_SET_URI`, `SERVICE_TOKEN`, `CORS_ALLOWED_ORIGINS`, `KAFKA_BOOTSTRAP_SERVERS`, `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` (PROD).
PROD está escrito (`compose.yml`, `application-prod.yml`) pero **no se ha levantado** en un entorno real.

## Rutina
| Tarea | Comando |
|---|---|
| Levantar todo | `tools/e2e/levantar.sh` (`--sin-reset` conserva datos) |
| Tras reiniciar el equipo | `podman start` de `e2e-*` y `eduspace-keycloak`, luego `levantar.sh --sin-reset` |
| Reiniciar un servicio | detener el proceso y `cd services/<x>-ms && java -jar target/*.jar` (o `levantar.sh --sin-reset`) |
| Recompilar uno | `cd services/<x>-ms && ./mvnw -q -o clean package -DskipTests` |
| Ver salud | `curl localhost:<puerto>/actuator/health` |
| Jobs programados | pre-matrícula vencida, cuotas por vencer/vencidas y mora, recordatorios de tarea, cierre de exámenes y asistencia, limpieza de outbox, reintentos de notificaciones (ShedLock evita duplicados) |

## Problemas conocidos y solución
| Síntoma | Causa | Solución |
|---|---|---|
| Tras reiniciar `auth-ms` la web seguía «con sesión» pero sin datos | En DEV `auth-ms` generaba una clave nueva en cada arranque y el Gateway y los servicios rechazaban todos los tokens | Corregido: la clave de DEV se guarda en `~/.eduspace-dev/jwt-rsa-dev.der` y se reutiliza (los tokens sobreviven al reinicio; borrar el archivo para empezar de cero). Si un token deja de valer, la web avisa y manda al login; si un servicio no responde, aparece la franja «No pudimos conectar con el sistema» con *Reintentar* y *Volver a iniciar sesión* |
| 401 en todas las llamadas del portal | El frontend usa Keycloak pero los servicios confían en `auth-ms` (o al revés) | `levantar.sh` sin opción (Keycloak) con `npm start`; o `--auth-propio` con `npm run auth:eduspace` |
| Pantalla vacía tras entrar (token no válido) | Mismo caso; ahora el portal muestra un aviso | Igual que arriba |
| 500 durante ~1 min tras reiniciar un servicio | Eureka sigue enrutando a la instancia caída hasta que expira | Esperar a que el Gateway vea la nueva instancia |
| 429 al hacer muchas llamadas seguidas | Límite por IP del Gateway | Espaciar las peticiones; los scripts de `tools/demo` reintentan |
| Pruebas de integración fallan con `NoClassDefFound … PostgresIntegrationTestBase` | Testcontainers sin Docker | Con Podman: `systemctl --user start podman.socket`, `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock`, `TESTCONTAINERS_RYUK_DISABLED=true` |
| `ng serve` deja de responder en el 4200 | Proceso detenido | `cd frontend && nohup npx ng serve &` |
| Aviso `UNKNOWN_TOPIC_OR_PARTITION` en notificaciones | Un productor aún no arrancó | Se resuelve al arrancar el productor |
| Notificaciones no llegan a un usuario | Alumno/docente/apoderado sin `usuarioId` vinculado a su cuenta | Vincular la cuenta en la ficha |
| Cuotas sin generar | No hay tarifa del año | Crear la tarifa: el sistema genera las cuotas pendientes y resuelve la alerta |
