# EduSpace — Infraestructura inicial (equivalente a pagatu S1–S8)

**Alcance:** Config Server, Config Repo, Eureka, API Gateway, `auth-ms` (con su base `auth_db`), Kafka + Kafka UI, MinIO, Mailpit, Prometheus/Loki/Grafana, redes y Docker Compose.
**Quién:** se construye **en equipo en la primera semana**; todos dependen de estas piezas.
**Convención:** "debe hacer" = comportamiento del código; no se incluye código.

---

## 1. Orden de arranque y dependencias

```
Config Server  →  Eureka  →  Kafka / MinIO / Mailpit (infra)  →  auth-ms  →  API Gateway  →  servicios de negocio
```

| Pieza | Depende de | DEV | PROD local |
|---|---|---|---|
| Config Server | — | 18888 | 28888 |
| Eureka | Config Server | 18761 | 28761 |
| Kafka (externo) / interno | — | 19092 / kafka:9092 | 29092 |
| Kafka UI | Kafka | 18085 | 28085 |
| MinIO API / consola | — | 19000 / 19001 | 29000 / 29001 |
| Mailpit SMTP / web | — | 11025 / 18025 | — |
| auth-ms | Config, Eureka | 8090 | interno 8080 |
| API Gateway | Config, Eureka, auth-ms | 18080 | 28080 (interno 8080) |
| Prometheus / Loki / Grafana | — | 19090 / 13100 / 13000 | 29090 / 23100 / 23000 |
| Angular | Gateway | 4200 | — |

Los puertos de infraestructura coinciden con los de pagatu: ejecutar uno u otro (los puertos de aplicaciones y bases ya no chocan). En PROD local **solo el Gateway publica puerto de negocio**; todos los demás hablan por la red Docker `eduspace-prod-net` (externa, creada una vez).

Versiones (las mismas de pagatu): Java 21 · Spring Boot 4.1.1 (Gateway 4.0.8, Config 4.0.7) · Spring Cloud 2025.1.3 · Maven Wrapper · PostgreSQL 16-alpine · Kafka `apache/kafka:4.3.1` · Kafka UI `ghcr.io/kafbat/kafka-ui:v1.5.0` · MapStruct 1.6.3 · Resilience4j `resilience4j-spring-boot4` 2.4.0.

---

## 2. Repositorios

| Repositorio | Contenido |
|---|---|
| `eduspace-config-server` | Servidor de configuración |
| `eduspace-config-repo` | YAML por servicio y perfil |
| `eduspace-eureka-server` | Registro de servicios |
| `eduspace-api-gateway` | Puerta de entrada |
| `eduspace-infra` | Compose de Kafka, Kafka UI, MinIO, Mailpit, Prometheus, Loki, Grafana |
| `eduspace-auth-ms` | Autenticación |
| `eduspace-<servicio>-ms` | Uno por servicio de negocio (8) |
| `eduspace-ng`, `eduspace-mobile`, `eduspace-desktop` | Clientes |

---

## 3. Config Server (`eduspace-config-server`)

| Archivo | Debe hacer |
|---|---|
| `pom.xml` | Parent Spring Boot 4.0.7; `spring-cloud-config-server`, `actuator`; BOM Spring Cloud 2025.1.3; Java 21. |
| `ConfigServerApplication` | Clase principal que activa el servidor de configuración. |
| `application.yml` | Nombre `config-server`; puerto 18888; perfil `native`; ubicación de búsqueda desde `CONFIG_REPO_LOCATION` (por defecto `file:./config-repo`); exponer `health` e `info`. |
| `Dockerfile` | Multi-etapa (Maven 3.9.9 + Temurin 21 → JRE 21); usuario no root; expone 8888. |
| `compose.yml` | Contenedor en `eduspace-prod-net`; volumen de solo lectura con el config-repo; healthcheck sobre `/actuator/health`. |
| `.env.example` | `CONFIG_REPO_LOCATION`. |

Comportamiento: cada cliente pide `{spring.application.name}-{profile}.yml` al arrancar. **PROD:** los clientes deben fallar rápido y reintentar si el Config Server no responde (nunca arrancar con valores por defecto en silencio).

## 4. Config Repo (`eduspace-config-repo`)

Un archivo por servicio y perfil (`dev`, `prod`). Contenido por archivo:

| Archivo | Debe contener |
|---|---|
| `api-gateway-{dev,prod}.yml` | Puerto; Eureka; `jwk-set-uri` de auth-ms; **CORS**; límites de tamaño; rutas (§7.4). |
| `auth-ms-{dev,prod}.yml` | Puerto; datasource `auth_db`; vigencia de access y refresh token; ubicación de las claves RSA (PROD). |
| `planificacion-ms-…`, `alumnos-ms-…`, `matricula-ms-…`, `asistencia-ms-…`, `tareas-ms-…`, `examenes-ms-…`, `calificaciones-ms-…`, `pago-ms-…`, `notificaciones-ms-…` | Puerto; datasource propio; Eureka; `jwk-set-uri`; Kafka (productor y/o consumidor con su `group-id`); tópicos `app.kafka.topic.*`; MinIO si aplica; Feign y Resilience4j si aplica; zona horaria. |

Reglas:
- **DEV:** host de BD `localhost` y puertos 15440–15449. **PROD:** host = nombre del contenedor, puerto interno 5432.
- Las **credenciales no van en el repo de configuración**: llegan por variables de entorno (`${DB_PASSWORD}`).
- Todos los servicios fijan `TZ=America/Lima` y `hibernate.jdbc.time_zone=America/Lima`.
- `spring.jpa.hibernate.ddl-auto: validate`; Flyway activo; `open-in-view: false`.

## 5. Eureka (`eduspace-eureka-server`)

| Archivo | Debe hacer |
|---|---|
| `pom.xml` | `spring-cloud-starter-netflix-eureka-server`, `actuator`. |
| `EurekaServerApplication` | Activa el servidor Eureka. |
| `application.yml` | Nombre `eureka-server`; puerto 18761; no se registra ni busca registro; DEV: self-preservation desactivado y expiración corta; PROD: self-preservation activo. |
| `Dockerfile`, `compose.yml` | Mismo patrón; arranca cuando el Config Server está sano. |

Los clientes se registran con `instance-id` = `${spring.application.name}:${server.port}` (DEV, hostname `localhost`) o `${random.value}` (PROD, permite réplicas) y se resuelven con `lb://nombre`.

## 6. Kafka, MinIO, Mailpit y observabilidad (`eduspace-infra`)

### 6.1 Kafka
| Archivo | Debe hacer |
|---|---|
| `kafka/compose.yml` | Kafka KRaft de un nodo; listeners `INTERNAL` (kafka:9092, contenedores), `EXTERNAL` (localhost:19092, apps con Maven) y `CONTROLLER`; `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`; volumen persistente. |
| Kafka UI | En 18085 apuntando a `kafka:9092`; para ver tópicos, mensajes y grupos. |

Tópicos: los crea **el servicio productor** al arrancar (3 particiones, 1 réplica). Convención de nombres: `entidad.accion-en-guiones` en minúsculas (catálogo completo en el informe general §3.2). Cada consumidor usa `group-id` = nombre del servicio.

Estándar de eventos: sobre con `eventId`, `eventType`, `occurredAt`, `version`, `traceId` (informe general §5.1). Serialización JSON sin cabeceras de tipo; el consumidor define tipo por defecto y paquetes de confianza propios, con `ErrorHandlingDeserializer`.

### 6.2 MinIO
| Elemento | Debe hacer |
|---|---|
| `minio/compose.yml` | Un contenedor con volumen; credenciales por `.env`. |
| Buckets | `alumnos-documentos` (alumnos-ms), `tareas-entregas` (tareas-ms), `justificaciones` (asistencia-ms), `boletines` (calificaciones-ms) y `comprobantes` (pago-ms). Todos **privados**. |
| Acceso | Los servicios generan **URL firmadas** de subida y descarga con vencimiento corto. El endpoint público usado para firmar debe ser el que el navegador puede alcanzar (no el nombre interno de Docker). |

### 6.3 Mailpit (solo DEV)
SMTP falso en 11025 con interfaz web en 18025, para probar los correos de `notificaciones-ms` sin enviarlos de verdad.

### 6.4 Observabilidad
| Archivo | Debe hacer |
|---|---|
| `prometheus/prometheus.yml` | Recolectar `/actuator/prometheus` de cada servicio (por Eureka o lista fija en DEV). |
| `loki` + `promtail` o driver de Docker | Recibir los logs de los contenedores. |
| `grafana/provisioning` | Fuentes de datos Prometheus y Loki; panel por servicio: peticiones/seg, errores 5xx, latencia p95, circuit breakers abiertos, **lag de consumidores Kafka**, eventos pendientes en outbox. |

Todos los servicios exponen `/actuator/health`, `info` y `prometheus`. Los logs usan el patrón con `[%X{traceId}]` y líneas estructuradas `component=… eventType=… key=… status=…`.

### 6.5 Redes
Una red externa `eduspace-prod-net` creada manualmente una vez; todos los compose de PROD la declaran como externa. En DEV, solo las bases, Kafka, MinIO, Mailpit y la observabilidad corren en Docker; las aplicaciones corren con Maven.

---

## 7. API Gateway (`eduspace-api-gateway`)

### 7.1 Archivos
| Archivo | Debe hacer |
|---|---|
| `pom.xml` | `spring-cloud-starter-gateway-server-webmvc`, cliente Eureka, cliente Config, `oauth2-resource-server`, `actuator`, `micrometer-registry-prometheus`. |
| `ApiGatewayApplication` | Clase principal. |
| `application.yml` | Mínimo: nombre `api-gateway`, perfil `dev`, `import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:18888}`. |
| `SecurityConfig` | Cadena **stateless**; valida el JWT con la clave pública de auth-ms; convierte `realm_access.roles` en autoridades con prefijo `ROLE_`; aplica `hasRole` por ruta; permite sin token `POST /api/v1/auth/login`, `/.well-known/jwks.json` y `/actuator/health`; 401 sin token, 403 con rol insuficiente. |
| `CorsConfig` | Permite los orígenes de Angular (4200 en DEV; dominio en PROD), Electron y Flutter web; métodos y cabeceras necesarios (`Authorization`, `Content-Type`, `X-Trace-ID`); expone `X-Trace-ID` y `Location`. **Sin esto el navegador bloquea todas las llamadas.** |
| `CorrelationIdFilter` | Si falta `X-Trace-ID`, lo genera; lo pone en el MDC, lo reenvía a los servicios y lo devuelve en la respuesta. |
| `logback-spring.xml` | Consola + archivo rotativo `logs/api-gateway.log` (7 días) con `[%X{traceId}]`. |
| `Dockerfile`, `compose.yml` | En PROD es el **único** contenedor con puerto publicado (28080→8080); recomendado 2 réplicas. |

### 7.2 Comportamientos clave
- Resolución por Eureka con `lb://` y balanceo round-robin.
- Las rutas internas `/internal/**` y `/actuator/**` **no se enrutan**.
- Límites: tamaño máximo de petición (los archivos grandes no pasan por el Gateway: van directo a MinIO con URL firmada); tiempos de espera de conexión y respuesta definidos; respuesta uniforme cuando un servicio no responde (503 con `traceId`).
- Punto único de entrada: es un punto único de falla; en PROD 2 réplicas detrás de un balanceador.

### 7.3 Seguridad por rol (grueso)
| Ruta | Sin token | ALUMNO | APODERADO | DOCENTE | SECRETARIA | CAJERO | ADMIN |
|---|---|---|---|---|---|---|---|
| `POST /auth/login` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `/auth/usuarios/**` | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✔ |
| `/planificacion/**` lectura | ✘ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `/planificacion/**` escritura | ✘ | ✘ | ✘ | ✘ | parcial | ✘ | ✔ |
| `/alumnos/**`, `/apoderados/**` | ✘ | propio | hijos | lectura | ✔ | ✘ | ✔ |
| `/matriculas/**` | ✘ | propia | hijos (lectura) | lectura | ✔ | ✘ | ✔ |
| `/asistencia/**` | ✘ | propia | hijos | sus secciones | lectura | ✘ | ✔ |
| `/tareas/**` | ✘ | propias | hijos (lectura) | sus cursos | ✘ | ✘ | ✔ |
| `/examenes/**` | ✘ | rendir | ✘ | sus cursos | ✘ | ✘ | ✔ |
| `/calificaciones/**` | ✘ | propias | hijos | sus cursos | lectura | ✘ | ✔ |
| `/pagos/**` | ✘ | ✘ | hijos (ver/pagar) | ✘ | lectura | ✔ | ✔ |
| `/notificaciones/mias/**` | ✘ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

El Gateway aplica el **rol grueso**; cada servicio aplica la **regla fina** ("solo mis hijos", "solo mis cursos").

### 7.4 Rutas
| Id | Destino | Predicado |
|---|---|---|
| `auth-ms` | `lb://auth-ms` | `/api/v1/auth/**` |
| `planificacion-ms` | `lb://planificacion-ms` | `/api/v1/planificacion/**` |
| `alumnos-ms` | `lb://alumnos-ms` | `/api/v1/alumnos/**`, `/api/v1/apoderados/**` |
| `matricula-ms` | `lb://matricula-ms` | `/api/v1/matriculas/**` |
| `asistencia-ms` | `lb://asistencia-ms` | `/api/v1/asistencia/**` |
| `tareas-ms` | `lb://tareas-ms` | `/api/v1/tareas/**` |
| `examenes-ms` | `lb://examenes-ms` | `/api/v1/examenes/**` |
| `calificaciones-ms` | `lb://calificaciones-ms` | `/api/v1/calificaciones/**` |
| `pago-ms` | `lb://pago-ms` | `/api/v1/pagos/**` |
| `notificaciones-ms` | `lb://notificaciones-ms` | `/api/v1/notificaciones/**` |

---

## 8. auth-ms (puerto 8090) — base `auth_db`

Base creada en `sql/auth-ms/V1__init_auth.sql` (4 tablas: `roles`, `usuarios`, `usuario_roles`, `refresh_tokens`).

### 8.1 Qué cubre
Login, emisión de JWT RS256, publicación de la clave pública (JWKS), alta y administración de usuarios, cambio y restablecimiento de contraseña, bloqueo por intentos fallidos y refresh token.

### 8.2 Tablas y semillas
- **`usuarios`**: `username` y `email` únicos; contraseña en BCrypt; `intentos_fallidos`, `bloqueado_hasta`, `debe_cambiar_clave`, `ultimo_login`.
- **`roles`** sin prefijo `ROLE_`: ADMIN, SECRETARIA, DOCENTE, ALUMNO, APODERADO, CAJERO.
- **`refresh_tokens`**: guarda el **hash** del token, vencimiento y estado revocado.
- **Semillas (solo DEV):** `admin / admin123` (ADMIN), `secretaria / secre123` (SECRETARIA), `cajero / cajero123` (CAJERO). Cambiar en PROD.

### 8.3 Archivos
| Archivo | Debe hacer |
|---|---|
| `pom.xml` | Web, data-jpa, validation, postgresql, flyway, `oauth2-resource-server` + Nimbus (firmado JWT), lombok, mapstruct, actuator, prometheus, springdoc, Eureka y Config clients. |
| `entity/Usuario`, `Rol`, `RefreshToken` | Mapear las tablas; la contraseña nunca se expone. |
| `repository/*` | Buscar usuario por username/email con roles; refresh por hash; limpiar vencidos. |
| `config/JwtKeyConfig` | **DEV:** genera par RSA 2048 al arrancar. **PROD:** carga clave privada y pública desde archivo/secret con `kid` fijo; si no la encuentra, el servicio **no arranca**. Prepara el JWKS con una o dos claves (rotación). |
| `config/SecurityConfig` | Stateless; permite `login`, `refresh`, `/.well-known/jwks.json`, salud; el resto protegido; `BCryptPasswordEncoder`. |
| `service/JwtService` | Emite access token RS256 con `sub`, `iss`, `exp`, `iat`, `preferred_username`, `email`, `usuarioId`, `realm_access.roles`; vigencia corta (p. ej. 30 min). |
| `service/AuthService` | Valida credenciales; usuario inexistente o clave errónea devuelven **el mismo** error; suma intentos fallidos y bloquea 15 min tras 5; reinicia al acertar; rechaza usuarios inactivos. |
| `service/RefreshTokenService` | Crea refresh token (se entrega una vez, se guarda su hash), lo rota en cada uso y lo revoca al cerrar sesión o cambiar clave. |
| `service/UsuarioService` | (ADMIN) crear usuario, asignar roles, activar/desactivar, desbloquear, restablecer clave temporal con `debe_cambiar_clave`. Evita quitar el último ADMIN. |
| `controller/AuthController` | `POST /api/v1/auth/login` → `access_token`, `refresh_token`, `token_type`, `expires_in`; `POST /refresh`; `POST /logout`; `POST /cambiar-clave`. |
| `controller/JwksController` | `GET /.well-known/jwks.json`. |
| `controller/UsuarioController` | `/api/v1/auth/usuarios` CRUD y acciones (solo ADMIN). |
| `exception/*` y `filter/CorrelationIdFilter` | 401 credenciales inválidas, 423 usuario bloqueado, 404, 400, 409 duplicado. |
| `db/migration/V1__init_auth.sql` | El script entregado. |

### 8.4 Consideraciones
- **Clave persistente en PROD** (informe general C3): sin ella, cada reinicio invalida todos los tokens y dos réplicas firman con claves distintas.
- Los demás servicios guardan solo `usuario_id`; el vínculo persona ↔ usuario vive en `alumnos_db` y `planificacion_db`.
- Migración futura a Keycloak: solo cambia la propiedad del emisor (`issuer-uri`) en Gateway y servicios.

---

## 9. Plantilla común de cada servicio de negocio

Todo servicio sigue este molde (los informes de cada integrante solo detallan lo específico):

| Archivo / paquete | Debe hacer |
|---|---|
| `pom.xml` | Dependencias base (web, jpa, validation, postgresql, flyway, lombok, mapstruct, actuator, prometheus, springdoc, Eureka, Config, resource-server) + Kafka, Feign, Resilience4j, ShedLock, Caffeine, MinIO o Mail **solo si el servicio las usa**. |
| `mvnw`, `.mvn/` | Maven Wrapper. |
| `Dockerfile` | Multi-etapa, usuario no root, healthcheck. |
| `compose-dev.yml` | Solo PostgreSQL 16-alpine de ese servicio (puerto 154xx) con volumen. |
| `compose.yml` | App + PostgreSQL en `eduspace-prod-net`; BD sin puerto publicado. |
| `.env` / `.env.example` | Usuario/clave/BD y URL del Config Server (el `.env` real va en `.gitignore`). |
| `application.yml` | Nombre, perfil `dev`, `import: optional:configserver:…` (en PROD, sin `optional:`). |
| `logback-spring.xml` | Consola + archivo rotativo 7 días, `[%X{traceId}]`. |
| `config/SecurityConfig` | Resource server; convertidor de roles; reglas por método HTTP; `/internal/**` permitido; Swagger solo en DEV. |
| `config/OpenApiConfig` | Título, versión y esquema Bearer. |
| `config/Kafka*Config` | Productor/consumidor según corresponda; `KafkaTopicsConfig` crea los tópicos propios. |
| `config/ShedLockConfig` | Habilita bloqueo de tareas programadas (tabla `shedlock`). |
| `filter/CorrelationIdFilter` | Lee/genera `X-Trace-ID`, lo pone en MDC y respuesta. |
| `exception/*` | `ResourceNotFoundException` (404), `BusinessRuleException` (409), `GlobalExceptionHandler` con cuerpo uniforme. |
| `entity/`, `repository/`, `dto/`, `mapper/` | Entidades que reflejan exactamente el SQL; DTO de entrada/salida; MapStruct. |
| `service/` + `Impl` | Reglas de negocio; transacciones cortas; sin llamadas remotas dentro de transacciones largas. |
| `controller/` | API versionada, paginada, con códigos HTTP precisos. |
| `client/` | Feign + bean aparte con circuit breaker y fallback. |
| `messaging/`, `event/` | Productores/consumidores con sobre estándar, idempotencia y log estructurado. |
| `db/migration/V1__*.sql` | Script de base de datos entregado en el paquete. |
| `test/` | Unitarias de reglas; integración con PostgreSQL real (Testcontainers) que corre Flyway. |

---

## 10. Lista de verificación de infraestructura

1. `docker network create eduspace-prod-net`.
2. Config Server arriba y entrega `auth-ms-dev.yml`.
3. Eureka arriba; un servicio de prueba aparece registrado.
4. Kafka y Kafka UI arriba; se crea un tópico desde un servicio y se ve en la UI.
5. MinIO arriba con los 5 buckets privados; Mailpit recibe un correo de prueba.
6. `auth-ms`: `POST /login` devuelve token; `/.well-known/jwks.json` responde; 5 intentos fallidos bloquean.
7. Gateway: sin token → 401; con token y rol incorrecto → 403; con token válido pasa; el preflight CORS de Angular responde.
8. `X-Trace-ID` aparece en Gateway, servicio y respuesta.
9. Prometheus muestra los servicios; Grafana lee métricas y logs.
10. Reinicio de `auth-ms` en PROD **no** invalida tokens (clave persistente).
