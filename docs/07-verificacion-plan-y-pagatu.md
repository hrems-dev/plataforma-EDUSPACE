# Verificación del plan y de la arquitectura pagatu (2026-10-16)

Revisión **estática** (código, configuración y documentos). No se levantó ningún servicio; la evidencia de ejecución es la última corrida e2e (6 escenarios OK) y las 255 pruebas.
Referencia del plan: `docs/00-general/00-informe-general-todos-los-servicios.md`. Referencia pagatu: `appdistribucion/class/pagatu/`.

## 1. Funciones del plan

### 1.1 Huecos (H) — todos los del MVP están hechos
| # | Estado | Dónde |
|---|---|---|
| H1 `matriculas_local` | Hecho | asistencia, tareas, examenes, calificaciones, pago |
| H2 autorización de docente | Hecho | `/internal/docentes/usuario/{id}/asignaciones` + caché + deniega si cae |
| H3 autorización de apoderado | Hecho | endpoints internos de alumnos-ms |
| H4 vencimiento de pre-matrícula | Hecho | job con ShedLock + `matricula.anulada` |
| H5 criterios de evaluación (suma = 100) | Hecho | calificaciones-ms |
| H6 periodo/curso en eventos de nota | Hecho | tareas, examenes |
| H7 promoción y ratificación | Parcial | ratificación existe; "aprobó el año" lo decide secretaría (ver pendientes) |
| H8 idempotencia de cobro | Hecho | `idempotency_key` + `version` |
| H9 numeración de comprobantes | Hecho | `series_comprobante` con bloqueo |
| H10 caja | Hecho | `cajas_sesion` |
| H11 asistencia DIA/CURSO | Hecho | asistencia-ms |
| H12 justificaciones | Hecho | asistencia-ms |
| H13 examen con tiempo en servidor | Hecho | `vence_en` + job de cierre |
| H14–H17 | Fase 2 (fuera del MVP) | recuperación, panel del director, pasarela/SUNAT, alta automática de cuentas |

### 1.2 Cuellos de botella (C)
| # | Estado | Observación |
|---|---|---|
| C1 outbox | Hecho | matricula, pago (y además alumnos, planificacion) |
| C2 `eventos_procesados` | Hecho | todos los consumidores |
| C3 clave RSA persistente | Parcial | el código la carga de PEM en PROD; falta aprovisionar las claves reales |
| C4 ShedLock | Hecho | pago, notificaciones, asistencia, tareas, calificaciones, examenes, matricula |
| C5 caché + breaker | Parcial | caché en los consumidores; `calificaciones-ms` y `examenes-ms` usan Feign con fallback pero sin `@CircuitBreaker` propio (revisar) |
| C6 última vacante | Hecho | `UPDATE … WHERE ocupados<capacidad` |
| C7 examen simultáneo | Hecho | UPSERT + índice de intento único en curso |
| C8 subida directa a MinIO | Por verificar | confirmar que tareas/alumnos usan URL firmada y no pasan el archivo |
| **C9 Gateway: CORS y límites** | **Falta** | no hay CORS ni límite multipart en `eduspace-gateway-dev.yml`; el Gateway tampoco valida JWT |
| C10 config-server `fail-fast` en PROD | Por verificar | hoy `optional:` en todos los `application.yml` |
| C11 zona horaria | Parcial | `America/Lima` en 8 de 10; **faltan alumnos-ms, planificacion-ms y notificaciones-ms** |
| C13 DLT / `ErrorHandlingDeserializer` | Parcial | solo matricula (DLT) y notificaciones (ambos); faltan en pago, asistencia, tareas, examenes, calificaciones |
| C14 token de servicio en `/internal/**` | Falta | aceptado para el MVP |
| C15 sin datos de menores en eventos | Hecho (por diseño) | revisar al añadir salud |
| C16 limpieza de tablas técnicas | Parcial | `LimpiezaJob` en examenes y calificaciones; confirmar en el resto |

### 1.3 Catálogo de eventos
- Con productor y consumidor: matricula.*, pago.*, asistencia.falta-registrada, tarea.publicada/calificada/recordatorio, examen.publicado/calificado, boletin.generado, horario.publicado.
- **Sin consumidor**: `tarea.entregada`, `alumno.registrado`, `alumno.estado-cambiado`, `anio.activado`, `periodo.cambiado` (los cuatro últimos estaban previstos para fase 2).

### 1.4 Prueba de aceptación final
La cadena crear alumno → matricular → pagar → pensiones → asistencia → tarea → examen → cierre de periodo → boletín → notificaciones está cubierta por los 6 escenarios de `tools/e2e` (última corrida OK, con tokens reales de `auth-ms`).

### 1.5 Calendario del curso
Pendiente: S10 paneles de observabilidad **por servicio** (hay stack `obs/`, no se verificó un panel por servicio), S11 integración Angular (front), S13–S16 validación final y sustentación.

## 2. ¿Sigue la arquitectura de pagatu?

**Sí en lo esencial.**
- Estructura de repo: `infra/ kafka/ obs/ services/` como pagatu (no están `docs` en mkdocs, `uso-rapido`, `CLAUDE.md`).
- Estructura interna: `config / controller / dto / entity / exception / filter / repository / service / mapper` como pagatu; `CorrelationIdFilter` y `GlobalExceptionHandler` en los 10 servicios.
- Servicio de dominio: clases `@Service` concretas (igual que pagatu) en matricula, asistencia, tareas, pago, examenes, calificaciones y auth.
- Stack: Boot 4.1.1, Flyway, Config Server (`optional:`), Eureka, Gateway, Feign + Resilience4j, Kafka, Actuator + Prometheus + springdoc, Dockerfile y `compose*.yml` por servicio.
- JWT RS256 con roles en `realm_access.roles`.

**Desviaciones a anotar**
1. `application.yml` de pagatu es mínimo (nombre + `config.import`) y el resto vive en el Config Server; aquí cada servicio trae datasource, JPA y Kafka dentro de su `application.yml`.
2. Dos estilos de paquete: `pe.edu.eduspace` (alumnos, planificacion, notificaciones) y `pe.edu.upeu.eduspace` (el resto); pagatu usa `pe.edu.upeu.<servicio>`.
3. Hermes usa interfaz + `Impl` (12 + 12 + 16 clases); pagatu y el resto usan clase concreta.
4. Andre/Witmer declaran MapStruct en el `pom.xml` pero no tienen paquete `mapper` con `@Mapper`: mapean a mano o en el servicio (`MatriculaMapper` está en `service/`).
5. Autorización mezclada: `@PreAuthorize` (alumnos, calificaciones) frente a reglas en `SecurityConfig` (asistencia, examenes, matricula, pago, tareas).
6. Puertos propios (8090–8099) en vez de la convención 18xxx de pagatu (infra sí usa 18xxx).
7. Nombres sin prefijo `pagatu-`/`eduspace-` en los servicios de dominio (la infra sí lleva `eduspace-`).
8. Faltan `mkdocs.yml`, `uso-rapido/` y `CLAUDE.md` que pagatu sí tiene a nivel de raíz.

## 3. Segunda revisión contra la guía y el código de pagatu (2026-10-27)

Fuentes: `Ruta de aprendizaje Pagatu S1–S4`, `class/pagatu` (docs S01–S09, `OFICIAL_ESTRUCTURA_ESTANDAR.md`, `config-repo`, `SecurityConfig` del gateway) y `class/frontPagatu`.

### Corregido en esta revisión
| Tema | Pagatu | EduSpace antes | Ahora |
|---|---|---|---|
| Emisor de tokens en DEV | Keycloak (`config-repo/*-dev.yml`); `auth-ms` en PROD | `auth-ms` en DEV y Keycloak solo con la variable `JWK_SET_URI`; el front en modo Keycloak daba 401 en todo si el backend arrancaba con el valor por defecto | `*-dev.yml` apuntan a Keycloak (`18090`), `*-prod.yml` a `auth-ms`. `tools/e2e/levantar.sh` levanta Keycloak si falta y acepta `--auth-propio` (necesario para los escenarios e2e) |
| Trazabilidad (S1/S10: «Angular envía `X-Trace-ID`») | el filtro respeta el header | los 10 filtros lo respetaban, pero el front no lo enviaba y el CORS del gateway permitía `X-Correlation-Id`, no `X-Trace-ID` | el interceptor agrega `X-Trace-ID` (UUID) a cada llamada a la API; el gateway lo permite y lo expone. Probado: el `traceId` del error vuelve igual al enviado |
| Seguridad del front | token en `sessionStorage` | token en memoria (Keycloak) | además, el Bearer solo se envía a `/api/` del mismo origen (no a dominios ajenos); aviso visible si el backend rechaza el token |

### Sigue la guía (verificado)
- Puertos DEV: Config 18888, Eureka 18761, Gateway 18080, Prometheus 19090, Loki 13100, Grafana 13000, Kafka 19092 / UI 18085 (idénticos a pagatu); bases en 154xx.
- Config Server nativo con `-dev`/`-prod` y `spring.config.import: optional:configserver` en los 10 servicios.
- Eureka con las 10 instancias registradas; gateway con rutas `lb://` y punto único de entrada; `instance-id` aleatorio en PROD.
- Flyway manda y Hibernate valida (`ddl-auto: validate` en los 10); `V2+` para cambios (nunca se edita una migración aplicada: el cambio de nombres de Inicial es `V3`).
- Errores con `{codigo, mensaje, ruta, traceId, fecha}`; `/api/v1`; Swagger en `/swagger-ui.html` y OpenAPI en `/v3/api-docs` en los 10 (comprobado en vivo).
- Seguridad S7: Resource Server en gateway y servicios, roles desde `realm_access.roles`, `usuarioId` tomado del token (no del request).
- Kafka/outbox (S8–S9), Feign + Resilience4j (S6) en los servicios que consultan a otros.
- Front (S11): `core/{guards,interceptors,models,services}`, `features/`, `layout/`, `shared/`; `proxy.conf.json`; CORS del gateway por variable.

### Diferencias que quedan (decididas o pendientes)
1. `/actuator/metrics` responde 401 en `planificacion`, `alumnos` y `notificaciones` (la guía S1 lo consulta sin token). Es endurecimiento deliberado: solo `health`, `info` y `prometheus` son públicos. Los otros 7 servicios dejan todo `/actuator/**` abierto; conviene igualarlos a los 3 estrictos antes de PROD.
2. Falta `infra/compose.yml` que valide Config + Eureka + Gateway juntos (pagatu lo tiene; aquí cada pieza tiene su `compose*.yml`, solo el gateway).
3. Los servicios de dominio no llevan prefijo `eduspace-` (`alumnos-ms`, no `eduspace-alumnos-ms`) como pide la convención `<proyecto>-<contexto>-ms`. Cambiarlo toca Eureka, rutas del gateway, `config-repo`, Prometheus y e2e.
4. Login del front: pagatu usa el formulario propio con *password grant* contra Keycloak; EduSpace usa código + PKCE (la app nunca ve la clave). Se mantiene: es más seguro y la guía S7 lo presenta como el estándar.
5. Desviaciones 1–8 de la sección 2 siguen vigentes.
