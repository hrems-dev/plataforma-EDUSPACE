# EduSpace — Informe de microservicios y especificación de archivos

**Proyecto:** EduSpace — "Todo a la mano" · Curso DIST 2026-2 (UPeU) · Arquitectura base: **pagatu**
**Equipo:** Hermes · Andre Fabricio · Witmer
**Alcance de esta entrega:** (1) análisis de cobertura de cada microservicio, (2) infraestructura inicial equivalente a pagatu hasta la **S8**, (3) especificación archivo por archivo (sin código) de `planificacion-ms`, `alumnos-ms` y `notificaciones-ms`, (4) bases de datos de esos tres servicios (carpeta `eduspace-sql/`).

> Convención del documento: "**debe hacer**" describe el comportamiento que tendrá el código; no se incluye código.

---

## 0. Contenido

1. Decisiones de arquitectura
2. Mapa de microservicios (cobertura, faltantes, responsable)
3. Análisis detallado por microservicio
4. Puertos y convenciones (corregidos)
5. Estructura de repositorios
6. Infraestructura inicial (config, eureka, gateway, auth, Kafka, observabilidad)
7. `planificacion-ms` — archivo por archivo
8. `alumnos-ms` — archivo por archivo
9. `notificaciones-ms` — archivo por archivo
10. Contratos de eventos Kafka
11. Rutas del Gateway y seguridad por rol
12. Bases de datos (resumen)
13. Orden de implementación (hasta S8)
14. Decisiones pendientes y riesgos

---

## 1. Decisiones de arquitectura

### 1.1 `academico-ms` se divide
`academico-ms` concentraba ~20 tablas y 8 responsabilidades (alumnos, apoderados, docentes, año, grados, secciones, cursos, horarios, asistencia, notas, boletines). Es un "servicio dios": un cambio en notas obliga a redesplegar horarios, y un solo integrante sería dueño de todo. Se separa por **razón de cambio**, con nombres que dicen lo que hacen:

| Nuevo servicio | Qué hace (nombre = función) |
|---|---|
| `planificacion-ms` | Estructura del colegio: año, periodos, niveles, grados, secciones, aulas, cursos, malla, docentes, asignaciones y horario |
| `alumnos-ms` | Expediente del alumno: datos, apoderados, documentos, ficha de salud, historial de estado |
| `asistencia-ms` | Registro diario de asistencia, justificaciones y reportes |
| `calificaciones-ms` | Notas por curso/periodo, promedios, actas y boletines |

**Opción elegida: A (servicios separados).** Opción B (fusionar alumnos + planificación en `registro-escolar-ms`) queda como alternativa si el equipo prefiere menos servicios; la única diferencia en este documento sería un solo proyecto con las dos bases fusionadas. No se recomienda porque cambian por motivos distintos y permiten repartir trabajo.

### 1.2 Servicios finales (10)
`auth-ms`, `planificacion-ms`, `alumnos-ms`, `matricula-ms`, `asistencia-ms`, `calificaciones-ms`, `pago-ms`, `examenes-ms`, `tareas-ms`, `notificaciones-ms`.

### 1.3 Recortes de alcance (MVP)
- **Fuera del MVP:** planilla/permisos de personal, biblioteca, transporte, mensajería interna y foros. Se anotan como "fase 2".
- **Tarifas, cuotas y descuentos pertenecen a `pago-ms`** (queda resuelta la contradicción anterior): `matricula-ms` solo pregunta el monto a `pago-ms` o lo recibe por evento.

### 1.4 Un solo colegio (sin multi-tenant)
Las bases no llevan `tenant_id`. Es la opción más simple y suficiente para el curso. Si más adelante se quisiera servir a varios colegios, se agrega `tenant_id` + Row-Level Security en cada tabla (el archivo `attachment.txt` ya contiene ese patrón). Cambio aislado: nueva migración `V2__tenant.sql`.

### 1.5 Reglas heredadas de pagatu (se mantienen)
Database-per-Service · **sin FK entre bases** (solo ids) · Flyway con `ddl-auto: validate` · API versionada `/api/v1/...` · Config Server centralizado · Eureka · Gateway único punto de entrada · JWT RS256 emitido por `auth-ms` · Kafka para eventos · Feign + Resilience4j para llamadas síncronas · logs con `traceId`.

### 1.6 Asignación de trabajo propuesta

| Integrante | Servicios |
|---|---|
| **Hermes** | `planificacion-ms` · `alumnos-ms` · `notificaciones-ms` |
| **Andre Fabricio** | `matricula-ms` · `asistencia-ms` · `tareas-ms` |
| **Witmer** | `pago-ms` · `examenes-ms` · `calificaciones-ms` |

Infraestructura compartida (config, eureka, gateway, auth, Kafka, observabilidad): se hace en equipo en la primera semana, un repositorio por pieza, igual que pagatu.

---

## 2. Mapa de microservicios

| # | Servicio | Base de datos | Responsable | Puerto DEV | Estado en esta entrega |
|---|---|---|---|---|---|
| 1 | `auth-ms` | `auth_db` | Equipo (infra) | 8090 | Especificado (§6.4) |
| 2 | `planificacion-ms` | `planificacion_db` | Hermes | 8091 | **Especificado + SQL** |
| 3 | `alumnos-ms` | `alumnos_db` | Hermes | 8092 | **Especificado + SQL** |
| 4 | `matricula-ms` | `matricula_db` | Andre | 8093 | Solo análisis |
| 5 | `asistencia-ms` | `asistencia_db` | Andre | 8094 | Solo análisis |
| 6 | `calificaciones-ms` | `calificaciones_db` | Witmer | 8095 | Solo análisis |
| 7 | `pago-ms` | `pago_db` | Witmer | 8096 | Solo análisis |
| 8 | `examenes-ms` | `examenes_db` | Witmer | 8097 | Solo análisis |
| 9 | `tareas-ms` | `tareas_db` | Andre | 8098 | Solo análisis |
| 10 | `notificaciones-ms` | `notificaciones_db` | Hermes | 8099 | **Especificado + SQL** |

---

## 3. Análisis detallado por microservicio

Para cada uno: **Cubre** (lo que hará), **Le falta / riesgo** (lo que se puede olvidar), **Eventos** y **Dependencias**.

### 3.1 `auth-ms`
- **Cubre:** login, emisión de JWT RS256, publicación de JWKS, usuarios y roles (`ADMIN`, `SECRETARIA`, `DOCENTE`, `ALUMNO`, `APODERADO`, `CAJERO`).
- **Le falta:** alta de usuarios por ADMIN, cambio y recuperación de contraseña, bloqueo tras intentos fallidos, refresh token (opcional), vínculo usuario ↔ persona (se resuelve guardando `usuario_id` en alumnos/docentes/apoderados, no al revés).
- **Eventos:** ninguno en MVP (fase 2: `usuario.creado`).
- **Depende de:** nadie.

### 3.2 `planificacion-ms` (antes parte de `academico-ms`)
- **Cubre:** año académico (un solo ACTIVO), periodos, niveles, grados, secciones, aulas, cursos, malla curricular, docentes, asignación docente-curso-sección, bloques horarios, horario con validación de choques, publicación de horario, feriados.
- **Le falta / riesgo:** generación automática de horarios (fuera de MVP: se arma manualmente con validación); suplencias de docentes; capacidad de sección vs matriculados (el conteo real lo tiene `matricula-ms`); cierre de año con arrastre de datos.
- **Eventos que publica:** `horario.publicado`, `anio.activado`, `periodo.cambiado`.
- **Depende de:** nadie (es fuente de verdad). Lo consumen matricula, asistencia, calificaciones, examenes y tareas por Feign.
- **Riesgo:** al ser consultado por casi todos, debe ser **estable y rápido**: endpoints de lectura simples, paginados y con caché del lado consumidor.

### 3.3 `alumnos-ms` (antes parte de `academico-ms`)
- **Cubre:** alta/edición de alumno, apoderados, relación con parentesco, apoderado principal y responsable de pago, documentos del expediente, ficha de salud básica, historial de cambios de estado, búsqueda por nombre/documento, endpoint interno de contactos para notificaciones.
- **Le falta / riesgo:** foto y archivos (se suben a MinIO/S3; la base solo guarda la ruta); DNI duplicado (se resuelve con la restricción única); datos sensibles de menores (acceso solo por rol y registrar auditoría); alta de cuenta de usuario del alumno/apoderado (MVP: manual vía `auth-ms`, fase 2: automático por evento).
- **Eventos que publica:** `alumno.registrado`, `alumno.estado-cambiado`.
- **Depende de:** MinIO (documentos). Lo consumen matricula, notificaciones, asistencia, pago.

### 3.4 `matricula-ms`
- **Cubre:** matrícula del alumno en año/grado/sección, estados (PRE_MATRICULA, MATRICULADO, RETIRADO), ratificación anual, vacantes por sección.
- **Le falta:** validar deuda pendiente antes de ratificar (consulta a `pago-ms`); traslados de sección; constancia de matrícula (PDF para escritorio).
- **Eventos:** publica `matricula.creada`, `matricula.retirada`; consume `pago.confirmado` (para pasar a MATRICULADO).
- **Depende de:** `alumnos-ms`, `planificacion-ms`, `pago-ms`.

### 3.5 `asistencia-ms`
- **Cubre:** asistencia diaria por sección (y por curso en secundaria), justificaciones, reportes por alumno/sección/periodo.
- **Le falta:** cierre del registro por día, corrección con auditoría, tardanzas con minutos, bloqueo de feriados (consulta a `planificacion-ms`).
- **Eventos:** publica `asistencia.falta-registrada`.
- **Depende de:** `planificacion-ms`, `matricula-ms` (lista de alumnos de la sección).

### 3.6 `calificaciones-ms`
- **Cubre:** notas por curso/periodo, promedios, actas, boletín en PDF, escala de calificación.
- **Le falta:** cierre de periodo (bloquear ediciones), recuperación, orden de mérito, reaperturas con auditoría.
- **Eventos:** publica `boletin.generado`; consume `examen.calificado` y `tarea.calificada` (para promediar).
- **Depende de:** `planificacion-ms`, `matricula-ms`.

### 3.7 `pago-ms`
- **Cubre:** **tarifas**, conceptos (matrícula, pensión, otros), cuotas por alumno, descuentos, registro de pago (caja/online), comprobantes, mora.
- **Le falta:** conciliación de caja, anulaciones, pasarela de pago real (se simula en MVP), job diario de vencidos.
- **Eventos:** publica `pago.confirmado`, `pago.vencido`.
- **Depende de:** `alumnos-ms`.

### 3.8 `examenes-ms`
- **Cubre:** banco de preguntas, examen programado, rendición del alumno, calificación automática y manual.
- **Le falta:** aleatorización, límite de tiempo en servidor, reintentos, protección anti-copia (fase 2).
- **Eventos:** publica `examen.calificado`.
- **Depende de:** `planificacion-ms`.

### 3.9 `tareas-ms`
- **Cubre:** publicación de tareas por docente, entrega del alumno con archivo, calificación y retroalimentación.
- **Le falta:** control de entrega tardía, versiones de entrega, límite de tamaño y tipos de archivo (MinIO/S3).
- **Eventos:** publica `tarea.publicada`, `tarea.entregada`, `tarea.calificada`.
- **Depende de:** `planificacion-ms`, MinIO.

### 3.10 `notificaciones-ms`
- **Cubre:** bandeja por usuario, plantillas, canales IN_APP / EMAIL / PUSH, preferencias, dispositivos push, reintentos, anti-duplicado.
- **Le falta / riesgo:** tiempo real (MVP usa consulta periódica; SSE/WebSocket en fase 2); proveedor push real (FCM) — en MVP un `PushSender` de bitácora; plantillas HTML de correo.
- **Eventos:** **solo consume** (9 tópicos, §10).
- **Depende de:** `alumnos-ms` por Feign para resolver destinatarios (con circuit breaker).

---

## 4. Puertos y convenciones (corregidos)

> **Corrección:** la tabla anterior de EduSpace usaba 18081–18087 y Kafka 41092, lo que chocaba con la convención real de pagatu (por ejemplo, 18085 es Kafka UI). Se unifican los puertos con pagatu: **DEV = 1xxxx, PROD local = 2xxxx**.

### 4.1 Infraestructura

| Pieza | DEV | PROD local |
|---|---|---|
| Config Server | 18888 | 28888 |
| Eureka | 18761 | 28761 |
| API Gateway | 18080 | 28080 (interno 8080) |
| Kafka (externo) | 19092 | 29092 |
| Kafka UI | 18085 | 28085 |
| Prometheus | 19090 | 29090 |
| Loki | 13100 | 23100 |
| Grafana | 13000 | 23000 |
| Mailpit (SMTP / UI, solo DEV) | 11025 / 18025 | — |
| MinIO (API / consola) | 19000 / 19001 | 29000 / 29001 |
| Angular `eduspace-ng` | 4200 | — |

### 4.2 Servicios y bases

| Servicio | Puerto app DEV | Puerto BD DEV | Puerto BD PROD | Nombre BD |
|---|---|---|---|---|
| auth-ms | 8090 | 15440 | 25440 | auth_db |
| planificacion-ms | 8091 | 15441 | 25441 | planificacion_db |
| alumnos-ms | 8092 | 15442 | 25442 | alumnos_db |
| matricula-ms | 8093 | 15443 | 25443 | matricula_db |
| asistencia-ms | 8094 | 15444 | 25444 | asistencia_db |
| calificaciones-ms | 8095 | 15445 | 25445 | calificaciones_db |
| pago-ms | 8096 | 15446 | 25446 | pago_db |
| examenes-ms | 8097 | 15447 | 25447 | examenes_db |
| tareas-ms | 8098 | 15448 | 25448 | tareas_db |
| notificaciones-ms | 8099 | 15449 | 25449 | notificaciones_db |

Notas:
- En PROD cada servicio escucha en 8080 **dentro de Docker** y no publica puerto; solo el Gateway se expone (28080).
- Si se ejecuta pagatu y EduSpace en la misma máquina a la vez, los puertos de infraestructura coinciden: se usa uno u otro. Los puertos de apps y bases ya no chocan.
- Usuario/contraseña de BD en DEV: `eduspace` / `eduspace` (solo local; en `.env`, nunca en Git).

---

## 5. Estructura de repositorios

Un repositorio por pieza (como pagatu), mismo prefijo:

| Repositorio | Contenido |
|---|---|
| `eduspace-config-server` | Config Server |
| `eduspace-config-repo` | YAML de configuración por servicio y perfil |
| `eduspace-eureka-server` | Service Discovery |
| `eduspace-api-gateway` | Gateway |
| `eduspace-infra` | Compose de Kafka, Kafka UI, Prometheus, Loki, Grafana, MinIO, Mailpit y red |
| `eduspace-auth-ms` | Autenticación |
| `eduspace-planificacion-ms`, `eduspace-alumnos-ms`, `eduspace-notificaciones-ms`, … | Un repositorio por servicio |
| `eduspace-ng`, `eduspace-mobile`, `eduspace-desktop` | Clientes |

---

## 6. Infraestructura inicial (equivalente a pagatu S1–S8)

Orden de arranque en DEV: **Config Server → Eureka → Kafka (infra) → Auth → Gateway → servicios**.

### 6.1 `eduspace-config-server` (puerto 18888)

| Archivo | Debe hacer |
|---|---|
| `pom.xml` | Parent Spring Boot 4.0.7; dependencias `spring-cloud-config-server` y `actuator`; BOM Spring Cloud 2025.1.3; Java 21. |
| `ConfigServerApplication` | Clase principal marcada como servidor de configuración (`@EnableConfigServer`). |
| `application.yml` | Nombre `config-server`, puerto `18888`, perfil `native`, ubicación de búsqueda leída de `CONFIG_REPO_LOCATION` con valor por defecto `file:./config-repo`; exponer `health` e `info`. |
| `config-repo/` (o repo aparte) | Un archivo por servicio y perfil: `{spring.application.name}-{profile}.yml`. Ver §6.6. |
| `Dockerfile` | Build multi-etapa (Maven 3.9.9 + Temurin 21 → JRE 21); copia el jar; expone 8888; el repo de configuración se monta como volumen. |
| `compose.yml` | Servicio del Config Server en la red `eduspace-prod-net` (externa); volumen con el config-repo; healthcheck sobre `/actuator/health`. |
| `.env.example` | `CONFIG_REPO_LOCATION`, puerto. |

### 6.2 `eduspace-eureka-server` (puerto 18761)

| Archivo | Debe hacer |
|---|---|
| `pom.xml` | `spring-cloud-starter-netflix-eureka-server`, actuator. |
| `EurekaServerApplication` | Activa el servidor Eureka (`@EnableEurekaServer`). |
| `application.yml` | Nombre `eureka-server`; **no** se registra a sí mismo ni busca registro (`register-with-eureka: false`, `fetch-registry: false`); en DEV desactivar self-preservation y acortar expiración para ver las bajas rápido; en PROD mantener self-preservation. |
| `Dockerfile` / `compose.yml` | Igual patrón que Config Server; depende del Config Server sano. |

Los clientes se registran con `instance-id` = `${spring.application.name}:${server.port}` en DEV (hostname `localhost`) y `${random.value}` en PROD, para poder levantar réplicas.

### 6.3 `eduspace-api-gateway` (puerto 18080 / PROD 8080)

| Archivo | Debe hacer |
|---|---|
| `pom.xml` | `spring-cloud-starter-gateway-server-webmvc`, cliente Eureka, cliente Config, `oauth2-resource-server`, actuator, prometheus. |
| `ApiGatewayApplication` | Clase principal. |
| `application.yml` | Mínimo: nombre `api-gateway`, perfil `dev`, `import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:18888}`. |
| `config-repo/api-gateway-dev.yml` (y `-prod`) | Puerto, Eureka, `jwk-set-uri` apuntando a `auth-ms`, y la lista de rutas (§11). |
| `SecurityConfig` | Cadena de seguridad **stateless**: valida el JWT con la clave pública de `auth-ms`; convierte `realm_access.roles` a autoridades con prefijo `ROLE_`; aplica `hasRole` por ruta; permite sin token `POST /api/v1/auth/login` y `/actuator/health`; responde 401 sin token y 403 con rol insuficiente. |
| `CorrelationIdFilter` | Si la petición no trae `X-Trace-ID`, genera uno; lo coloca en el MDC y lo reenvía a los servicios; lo devuelve en la respuesta. |
| `logback-spring.xml` | Consola + archivo rotativo `logs/api-gateway.log` (7 días); patrón con `[%X{traceId}]`. |
| `Dockerfile` / `compose.yml` | En PROD es el **único** contenedor que publica puerto (28080→8080). |

Resolución: las rutas usan `lb://nombre-servicio` y el balanceo es round-robin por Eureka.

### 6.4 `eduspace-auth-ms` (puerto 8090) — S7

**Base `auth_db`**, tablas: `roles`, `usuarios`, `usuario_roles`. Roles sin prefijo `ROLE_` en BD. Semillas (solo DEV): `admin@eduspace.com` / `admin123` (ADMIN), `secretaria@eduspace.com` / `secre123` (SECRETARIA).

| Archivo | Debe hacer |
|---|---|
| `entity/Usuario`, `Rol` | Mapear las tres tablas; contraseña siempre en hash; estado activo/bloqueado. |
| `repository/UsuarioRepository`, `RolRepository` | Buscar por email/username con sus roles. |
| `config/JwtConfig` | Al arrancar genera un par de claves RSA 2048; construye el firmante (Nimbus) y el JWKS con la parte pública. |
| `config/SecurityConfig` | Cadena stateless; permite `login`, `/.well-known/jwks.json` y salud; protege el resto; `BCryptPasswordEncoder`. |
| `service/JwtService` | Emitir token RS256 con claims: `sub`, `iss`, `exp`, `preferred_username`, `email`, `usuarioId`, `realm_access.roles`. Vigencia configurable. |
| `service/AuthService` | Validar credenciales; usuario inexistente o clave errónea devuelven el **mismo** error (no revelar cuál). Registrar intentos fallidos. |
| `controller/AuthController` | `POST /api/v1/auth/login` → `access_token`, `token_type`, `expires_in`. |
| `controller/JwksController` | `GET /.well-known/jwks.json`. |
| `controller/UsuarioController` | (ADMIN) crear usuario, asignar roles, activar/bloquear, restablecer clave. |
| `exception/*` | 401 credenciales inválidas, 404, 400, 409 usuario duplicado. |
| `V1__init_auth.sql` | Tablas, índices únicos de email, semillas. |

Migración futura a Keycloak: solo cambia la propiedad `issuer-uri` de los servicios; el código no se toca.

### 6.5 Kafka y observabilidad (`eduspace-infra`) — S8

| Archivo | Debe hacer |
|---|---|
| `kafka/compose.yml` | Kafka `apache/kafka:4.3.1` en modo KRaft (un nodo); listeners `INTERNAL` (kafka:9092, para contenedores), `EXTERNAL` (localhost:19092, para apps desde Maven) y `CONTROLLER`; `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`; volumen de datos. |
| Kafka UI | `ghcr.io/kafbat/kafka-ui:v1.5.0` en 18085, apuntando a `kafka:9092`; para inspeccionar tópicos y mensajes. |
| `prometheus/prometheus.yml` | Recolectar `/actuator/prometheus` de cada servicio. |
| `loki` + `grafana` | Loki recibe logs; Grafana con Prometheus y Loki como fuentes. Panel básico: peticiones por servicio, errores, latencia. |
| `minio/compose.yml` | MinIO con buckets `alumnos-documentos` y `tareas-entregas`. |
| `mailpit` (solo DEV) | SMTP falso para probar los correos de `notificaciones-ms`; interfaz web en 18025. |
| Red | `eduspace-prod-net` creada una vez (`docker network create`) y declarada como **externa** en todos los compose de PROD. |

Tópicos y particiones: 3 particiones, 1 réplica (DEV). Los tópicos se crean desde cada servicio productor (`KafkaTopicsConfig`), nunca automáticamente.

### 6.6 `eduspace-config-repo`

| Archivo | Contenido |
|---|---|
| `api-gateway-dev.yml` / `-prod.yml` | Puerto, Eureka, JWKS, rutas. |
| `auth-ms-dev.yml` / `-prod.yml` | Puerto, datasource, vigencia JWT. |
| `planificacion-ms-dev.yml` / `-prod.yml` | Puerto 8091, datasource `planificacion_db`, Eureka, JWKS, Kafka producer, nombres de tópicos. |
| `alumnos-ms-dev.yml` / `-prod.yml` | Puerto 8092, datasource, Eureka, JWKS, Kafka producer, MinIO. |
| `notificaciones-ms-dev.yml` / `-prod.yml` | Puerto 8099, datasource, Eureka, JWKS, Kafka consumer, SMTP, Feign + Resilience4j, reintentos. |

DEV: host de BD `localhost` y puerto 154xx. PROD: host = nombre del contenedor y puerto interno 5432. Las credenciales llegan por variables de entorno, no por el repo de config.

---

## 7. `planificacion-ms` — archivo por archivo

**Paquete base:** `pe.edu.eduspace.planificacion` · **Puerto:** 8091 · **BD:** `planificacion_db` (14 tablas) · **Productor Kafka**, sin consumidores ni Feign en MVP.

### 7.1 Raíz del proyecto

| Archivo | Debe hacer |
|---|---|
| `pom.xml` | Spring Boot 4.1.1, Java 21; web, data-jpa, validation, postgresql, flyway (+ flyway-database-postgresql), lombok, mapstruct 1.6.3 con `annotationProcessorPaths` (lombok primero), actuator, micrometer-prometheus, springdoc, cliente Eureka, cliente Config, oauth2-resource-server, `spring-boot-starter-kafka`. |
| `mvnw`, `.mvn/` | Maven Wrapper para compilar sin instalar Maven. |
| `Dockerfile` | Multi-etapa (Maven → JRE 21), usuario no root, healthcheck. |
| `compose-dev.yml` | Solo PostgreSQL 16-alpine (BD `planificacion_db`, puerto 15441) con volumen; la app corre con Maven. |
| `compose.yml` | App + PostgreSQL en `eduspace-prod-net`; BD sin puerto publicado; variables desde `.env`. |
| `.env` / `.env.example` | Usuario, clave, nombre de BD, URL del Config Server. El `.env` real va en `.gitignore`. |
| `src/main/resources/application.yml` | Mínimo: nombre `planificacion-ms`, perfil `dev`, `import: optional:configserver:...`. |
| `logback-spring.xml` | Consola + archivo rotativo `logs/planificacion-ms.log`, 7 días, `[%X{traceId}]`. |
| `db/migration/V1__init_planificacion.sql` | El script entregado en `eduspace-sql/planificacion-ms/`. |

### 7.2 `config/`

| Clase | Debe hacer |
|---|---|
| `OpenApiConfig` | Título, versión y esquema de seguridad Bearer para Swagger. |
| `SecurityConfig` | Resource server por `jwk-set-uri`; stateless; convertidor de roles (agrega `ROLE_`); reglas: `GET` autenticado, `POST/PUT/PATCH/DELETE` solo `ADMIN` (y `SECRETARIA` en horarios/asignaciones); `/actuator/health` y Swagger abiertos en DEV; `/internal/**` permitido (no se expone por el Gateway). |
| `KafkaTopicsConfig` | Declarar los 3 tópicos que produce (`horario.publicado`, `anio.activado`, `periodo.cambiado`) con 3 particiones y 1 réplica; los nombres salen de `app.kafka.topic.*`. |
| `KafkaProducerConfig` | Serializador de clave String y de valor JSON **sin cabeceras de tipo**. |

### 7.3 `filter/` y `exception/`

| Clase | Debe hacer |
|---|---|
| `CorrelationIdFilter` | Leer `X-Trace-ID` (o generarlo), ponerlo en MDC y en la respuesta, limpiarlo al terminar. |
| `ResourceNotFoundException` | Recurso no encontrado → 404. |
| `BusinessRuleException` | Violación de regla de negocio → 409 (choque de horario, año ya activo, etc.). |
| `GlobalExceptionHandler` | Traduce excepciones a un cuerpo uniforme (código, mensaje, ruta, traceId, fecha): 404, 400 (validación con detalle por campo), 409 (reglas y violaciones de unicidad de BD), 500 genérico sin filtrar detalles internos. |

### 7.4 `entity/` (una por tabla)

`AnioAcademico`, `Periodo`, `Nivel`, `Grado`, `Aula`, `Seccion`, `Curso`, `GradoCurso`, `Docente`, `Asignacion`, `BloqueHorario`, `Horario`, `PublicacionHorario`, `Feriado`.
**Debe hacer:** mapear exactamente columnas y restricciones del SQL (Hibernate valida, no crea); ids generados por la BD; relaciones solo entre tablas de **esta** base; `usuario_id` de docente como número simple; marcas `creado_en`/`actualizado_en` donde existan; enumeraciones (estado, turno, tipo de aula) como texto, no ordinal.

### 7.5 `repository/`

Un repositorio por entidad. Consultas con significado de negocio:
- Año: obtener el ACTIVO; existe por nombre.
- Sección: listar por año y grado; contar por aula.
- Horario: buscar choques por **docente + día + bloque**, por **aula + día + bloque** y por **sección + día + bloque** (a través de la asignación); listar el horario completo de una sección o de un docente.
- Asignación: por docente, por sección.
- Docente: búsqueda paginada por apellidos/documento.
- Publicación: última versión por sección.

### 7.6 `dto/` y `mapper/`

- **Request** (entrada, con validaciones: obligatorios, tamaños, fechas coherentes) y **Response** (salida) por recurso. La entidad nunca se expone.
- Respuestas compuestas: `HorarioSeccionResponse` (cuadrícula día × bloque con curso, docente, aula), `HorarioDocenteResponse`, `MallaGradoResponse` (cursos del grado con horas), `ResultadoValidacionHorario` (lista de choques y de cursos con horas incompletas).
- `PageResponse` genérico para listados paginados.
- **Mappers MapStruct:** convierten entidad ↔ DTO; ignoran campos de auditoría y id en la entrada.

### 7.7 `service/` (interfaz + `Impl`) — reglas de negocio

| Servicio | Debe hacer |
|---|---|
| `AnioAcademicoService` | CRUD; **solo un año ACTIVO** (activar uno exige que el anterior esté CERRADO); fechas coherentes; al activar publica `anio.activado`; cerrar un año lo vuelve de solo lectura. |
| `PeriodoService` | CRUD dentro del rango del año; sin solapamiento entre periodos; número único por año; al cambiar de estado publica `periodo.cambiado`. |
| `NivelService`, `GradoService` | Consulta y edición menor (los datos base vienen de la semilla). |
| `AulaService` | CRUD; no desactivar un aula usada en horarios vigentes. |
| `SeccionService` | CRUD por año/grado; nombre único; **capacidad ≤ capacidad del aula**; el tutor debe ser docente ACTIVO. |
| `CursoService`, `MallaService` | CRUD de cursos; asociar cursos a grados con horas semanales; no repetir curso en un grado. |
| `DocenteService` | CRUD; documento único; búsqueda; vincular `usuario_id`; inactivar (no borrar) si tiene asignaciones. |
| `AsignacionService` | Asignar docente a curso en sección; el curso debe estar en la malla del grado de esa sección; una asignación por (sección, curso). |
| `BloqueHorarioService` | Definir franjas por turno; sin solapamiento dentro del turno. |
| `HorarioService` | Crear/mover/eliminar clases validando: (1) docente libre, (2) aula libre, (3) sección sin otra clase, (4) bloque del mismo turno que la sección, (5) no es receso, (6) no excede horas semanales de la malla. Devuelve error 409 con el motivo exacto. Método de **validación global** que lista cursos con horas faltantes o sobrantes. |
| `PublicacionHorarioService` | Publicar el horario de una sección: exige validación sin choques; incrementa versión; registra quién publicó; **después de confirmar la transacción** envía `horario.publicado` con los usuarios docentes involucrados. |
| `FeriadoService` | CRUD de feriados dentro del año; consulta "¿es hábil esta fecha?" para otros servicios. |

### 7.8 `messaging/` y `event/`

| Clase | Debe hacer |
|---|---|
| `event/HorarioPublicadoEvent` | Datos: id de sección, nombre de sección, año, versión, fecha, lista de `docenteUsuarioIds`. |
| `event/AnioActivadoEvent` | Id y nombre del año, fechas. |
| `event/PeriodoCambiadoEvent` | Id, número, nombre, estado, año. |
| `messaging/PlanificacionEventProducer` | Publicar cada evento con **clave = id de la entidad**, solo **después del commit** (no publicar si la transacción se revierte); registrar en log `component, eventType, key, status`; errores de envío se registran sin romper la operación de negocio. |

### 7.9 `controller/` — API (prefijo `/api/v1/planificacion`)

| Recurso | Endpoints | Roles |
|---|---|---|
| `/anios` | listar, obtener, crear, editar, `PATCH /{id}/activar`, `PATCH /{id}/cerrar`, `GET /activo` | lectura: autenticado; escritura: ADMIN |
| `/periodos` | CRUD + filtro `anioId` | ADMIN |
| `/niveles`, `/grados` | listar, obtener | autenticado |
| `/aulas` | CRUD, filtro por tipo | ADMIN |
| `/secciones` | CRUD, filtro `anioId`, `gradoId` | ADMIN |
| `/cursos` | CRUD | ADMIN |
| `/malla` | `GET /grado/{gradoId}`, `POST`, `PUT`, `DELETE` | ADMIN |
| `/docentes` | CRUD paginado, `PATCH /{id}/usuario` | ADMIN, SECRETARIA |
| `/asignaciones` | crear, eliminar, listar por sección/docente | ADMIN, SECRETARIA |
| `/bloques` | CRUD por turno | ADMIN |
| `/horarios` | `GET /seccion/{id}`, `GET /docente/{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `GET /validar/{seccionId}` | lectura: autenticado; escritura: ADMIN, SECRETARIA |
| `/horarios/publicar` | `POST /seccion/{id}` | ADMIN, SECRETARIA |
| `/feriados` | CRUD, `GET /es-habil?fecha=` | lectura autenticado |
| `/internal/...` | lecturas livianas para otros servicios (sección, grado, año activo, docente) | sin exponer en el Gateway |

Todos paginan cuando devuelven listas grandes; las respuestas usan códigos HTTP precisos (201 al crear con cabecera `Location`, 204 al eliminar, 404, 409).

### 7.10 `test/`

- Unitarias de `HorarioService` (cada choque), `SeccionService` (capacidad) y `AnioAcademicoService` (año único activo).
- Prueba de integración con PostgreSQL real (Testcontainers) que ejecuta Flyway y verifica el esquema.
- Prueba del productor: el evento solo sale tras commit.

---

## 8. `alumnos-ms` — archivo por archivo

**Paquete base:** `pe.edu.eduspace.alumnos` · **Puerto:** 8092 · **BD:** `alumnos_db` (6 tablas) · **Productor Kafka** · Cliente de MinIO.

### 8.1 Raíz del proyecto
Mismos archivos que §7.1, con estas diferencias: `compose-dev.yml` usa BD `alumnos_db` en puerto 15442; `pom.xml` agrega el SDK de MinIO (o S3 compatible); `application.yml` mínimo con nombre `alumnos-ms`; `V1__init_alumnos.sql` es el script entregado.

### 8.2 `config/`

| Clase | Debe hacer |
|---|---|
| `OpenApiConfig` | Igual que §7.2. |
| `SecurityConfig` | Resource server; reglas: lectura `ADMIN`, `SECRETARIA`, `DOCENTE`; escritura `ADMIN`, `SECRETARIA`; **`ALUMNO` y `APODERADO` solo ven lo propio** (el controlador compara el `usuarioId` del token con el del alumno o con los hijos del apoderado); `/internal/**` permitido. |
| `KafkaTopicsConfig`, `KafkaProducerConfig` | Tópicos `alumno.registrado` y `alumno.estado-cambiado`. |
| `StorageConfig` | Cliente MinIO con endpoint, credenciales y bucket `alumnos-documentos` desde configuración; crear el bucket si no existe al arrancar. |

### 8.3 `filter/` y `exception/`
Igual que §7.3, más `DuplicateResourceException` → 409 (documento ya registrado) y `StorageException` → 502 (fallo del almacenamiento).

### 8.4 `entity/`
`Alumno`, `Apoderado`, `AlumnoApoderado`, `AlumnoDocumento`, `AlumnoSalud`, `AlumnoEstadoHistorial`.
**Debe hacer:** reflejar el SQL; `AlumnoSalud` comparte clave con `Alumno` (relación uno a uno); `AlumnoApoderado` guarda parentesco y banderas (principal, responsable de pago, vive con alumno, puede recoger).

### 8.5 `repository/`
- Alumno: búsqueda paginada por texto (nombres, apellidos, documento, código) y filtro por estado; existe por documento; último código generado.
- Apoderado: por documento; por `usuario_id`; hijos asociados.
- AlumnoApoderado: por alumno, por apoderado; quitar la bandera "principal" a los demás.
- Historial: por alumno, orden descendente.

### 8.6 `dto/` y `mapper/`
- `AlumnoCreateRequest` (datos personales + lista de apoderados con parentesco), `AlumnoUpdateRequest`, `AlumnoResponse`, `AlumnoResumenResponse` (para listados), `ApoderadoRequest/Response`, `VinculoApoderadoRequest`, `DocumentoResponse`, `SaludRequest/Response`, `CambioEstadoRequest`, `HistorialResponse`.
- `ContactosNotificacionResponse` (contrato interno con `notificaciones-ms`): `alumnoId`, `nombreCompleto`, `usuarioId`, lista de apoderados con `usuarioId`, nombres, email, teléfono y `esResponsablePago`.
- Validaciones: fecha de nacimiento no futura, sexo M/F, documento con formato según tipo, correo válido.

### 8.7 `service/`

| Servicio | Debe hacer |
|---|---|
| `AlumnoService` | Alta: validar documento único, **generar código** (p. ej. año + correlativo), guardar alumno y apoderados en una sola transacción, exigir **un** apoderado principal y como máximo **un** responsable de pago; al terminar publicar `alumno.registrado`. Edición, búsqueda, consulta por id. Vincular/desvincular `usuario_id`. |
| `ApoderadoService` | Crear o reutilizar un apoderado existente por documento (un apoderado con varios hijos); actualizar datos de contacto; listar hijos. |
| `VinculoService` | Agregar/quitar apoderado de un alumno; cambiar principal/responsable de pago; impedir dejar al alumno sin apoderado principal. |
| `EstadoAlumnoService` | Cambiar estado (RETIRADO, EGRESADO, TRASLADADO, INACTIVO, ACTIVO) con motivo obligatorio; registrar historial con el usuario que lo hizo; publicar `alumno.estado-cambiado`. No se borra físicamente a un alumno. |
| `DocumentoService` | Subir archivo a MinIO (valida tipo y tamaño), guardar metadatos, descargar con URL temporal firmada, marcar verificado, eliminar. |
| `SaludService` | Crear/actualizar la ficha de salud; **acceso restringido** (ADMIN, SECRETARIA y docente tutor); registrar en log quién consulta (dato sensible de menores). |
| `ContactosService` | Armar `ContactosNotificacionResponse` para uso interno. |

### 8.8 `messaging/` y `event/`
- `AlumnoRegistradoEvent`: id, código, nombre completo, fecha de nacimiento, lista de ids de apoderados y de sus `usuarioId`.
- `AlumnoEstadoCambiadoEvent`: id, estado anterior, estado nuevo, motivo, fecha.
- `AlumnoEventProducer`: igual que §7.8 (clave = id del alumno, después del commit, log estructurado).

### 8.9 `controller/` — API

| Prefijo | Endpoints | Roles |
|---|---|---|
| `/api/v1/alumnos` | `GET` (paginado y filtros), `GET /{id}`, `POST`, `PUT /{id}`, `PATCH /{id}/usuario` | ADMIN, SECRETARIA; DOCENTE lectura; ALUMNO/APODERADO solo lo propio |
| `/api/v1/alumnos/{id}/estado` | `PATCH` (cambia estado), `GET /historial` | ADMIN, SECRETARIA |
| `/api/v1/alumnos/{id}/apoderados` | listar, agregar, editar vínculo, quitar | ADMIN, SECRETARIA |
| `/api/v1/alumnos/{id}/documentos` | listar, subir (multipart), descargar, verificar, eliminar | ADMIN, SECRETARIA |
| `/api/v1/alumnos/{id}/salud` | `GET`, `PUT` | ADMIN, SECRETARIA, docente tutor |
| `/api/v1/apoderados` | `GET` (búsqueda), `GET /{id}`, `PUT /{id}`, `GET /{id}/hijos` | ADMIN, SECRETARIA; APODERADO solo sus datos |
| `/api/v1/alumnos/mios` | alumnos del usuario autenticado (para app móvil del apoderado) | APODERADO, ALUMNO |
| `/internal/alumnos/{id}/contactos` | `GET` contactos para notificar | **No expuesto por el Gateway** |
| `/internal/alumnos/{id}` | `GET` datos básicos (nombre, estado) para otros servicios | No expuesto |

### 8.10 `test/`
- Alta con apoderados (principal único, responsable de pago único).
- Documento duplicado → 409.
- Cambio de estado crea historial y evento.
- Un APODERADO no puede ver alumnos ajenos.
- Subida de documento con almacenamiento simulado.

---

## 9. `notificaciones-ms` — archivo por archivo

**Paquete base:** `pe.edu.eduspace.notificaciones` · **Puerto:** 8099 · **BD:** `notificaciones_db` (5 tablas) · **Consumidor Kafka** · **Cliente Feign** hacia `alumnos-ms` · Correo SMTP.

### 9.1 Raíz del proyecto
Igual que §7.1 con: BD `notificaciones_db` (puerto 15449); `pom.xml` agrega OpenFeign, `resilience4j-spring-boot4` (2.4.0), `spring-boot-starter-mail`; `compose-dev.yml` incluye además **Mailpit** (SMTP 11025, web 18025).

### 9.2 `config/`

| Clase | Debe hacer |
|---|---|
| `SecurityConfig` | Resource server; `/api/v1/notificaciones/mias/**`, preferencias y dispositivos: cualquier usuario autenticado actuando sobre **sus propios datos**; plantillas y envío manual: ADMIN. |
| `KafkaConsumerConfig` | Deserializador con manejo de errores que delega en JSON; tipo por defecto y paquetes de confianza propios; `group-id = notificaciones-ms`; una fábrica de listeners con reintentos acotados para eventos con error. |
| `FeignConfig` | Habilitar clientes Feign; interceptor que propaga `X-Trace-ID`. |
| `Resilience4jConfig` (o YAML) | Instancia `alumnosClient`: ventana 5, umbral de fallo 50 %, espera en estado abierto 10 s, 3 llamadas en medio-abierto. |
| `SchedulingConfig` | Habilitar tareas programadas (reintentos). |
| `MailConfig` | Remitente por defecto y parámetros SMTP desde configuración. |
| `OpenApiConfig` | Igual que en los demás. |

### 9.3 `client/`

| Clase | Debe hacer |
|---|---|
| `AlumnosClient` | Interfaz Feign del servicio `alumnos-ms` (resuelto por Eureka): `GET /internal/alumnos/{id}/contactos`. |
| `ContactosDto` | Copia local del contrato de contactos (propiedad de este servicio; no se comparte código entre servicios). |
| `AlumnosGateway` (bean aparte) | Envuelve la llamada Feign con el circuit breaker y el método *fallback*. Debe ser **otro bean** distinto del que lo invoca: la anotación no funciona si se llama a un método de la misma clase. El *fallback* devuelve "contactos no disponibles" para que el consumidor deje la notificación **PENDIENTE** y se reintente, en lugar de perderla. |

### 9.4 `entity/` y `repository/`
- Entidades: `PlantillaNotificacion`, `Notificacion`, `PreferenciaNotificacion`, `DispositivoPush`, `IntentoEnvio`. Los datos extra del evento se guardan como JSON.
- Repositorios con consultas: bandeja paginada por usuario y filtro leída/no leída; conteo de no leídas; marcar todas como leídas; pendientes/fallidas con menos de 3 intentos; preferencia por usuario/tipo/canal; dispositivos activos por usuario; plantilla por código y canal.

### 9.5 `dto/` y `mapper/`
`NotificacionResponse`, `ConteoNoLeidasResponse`, `PreferenciaRequest/Response`, `DispositivoRequest`, `PlantillaRequest/Response`, `EnvioManualRequest` (destinatarios o rol, título, mensaje, canales), `PageResponse`. Mappers MapStruct.

### 9.6 `event/` (copias propias del contrato)
`MatriculaCreadaEvent`, `PagoConfirmadoEvent`, `PagoVencidoEvent`, `AsistenciaFaltaRegistradaEvent`, `TareaPublicadaEvent`, `TareaCalificadaEvent`, `ExamenCalificadoEvent`, `BoletinGeneradoEvent`, `HorarioPublicadoEvent`. Cada una con **solo los campos que este servicio necesita** (el resto se ignora al deserializar), según las tablas de §10.

### 9.7 `messaging/` — consumidores

| Clase | Debe hacer |
|---|---|
| `NotificacionEventConsumer` | Un método de escucha por tópico. Cada uno: (1) registra en log `component, eventType, key`; (2) determina **tipo de plantilla** y **destinatarios**; (3) delega en `NotificacionOrquestador`. Un evento mal formado se descarta con log (no bloquea la cola). |

Destinatarios por evento:
- Eventos de un alumno (matrícula, pago, falta, tarea, examen, boletín): se consultan los **contactos** del alumno (Feign) y se notifica al alumno (si tiene usuario) y a sus apoderados; los de **pago** van al **responsable de pago**.
- `horario.publicado`: los `docenteUsuarioIds` ya vienen en el evento (sin Feign). Notificar a alumnos de la sección queda para fase 2, cuando `matricula-ms` exponga la lista de la sección.

### 9.8 `service/`

| Servicio | Debe hacer |
|---|---|
| `NotificacionOrquestador` | Para cada destinatario y canal habilitado: consultar preferencia; armar mensaje con la plantilla; guardar la notificación; si es IN_APP, queda ENVIADA de inmediato; si es EMAIL/PUSH, delegar al envío. La restricción única de BD evita duplicados (Kafka entrega al menos una vez): si salta, se ignora y se sigue. |
| `PlantillaService` | Resolver plantilla por código y canal; reemplazar marcadores `{{campo}}` con los datos del evento; si falta un dato, dejar un texto neutro en lugar de fallar. CRUD para ADMIN. |
| `NotificacionService` | Bandeja del usuario autenticado paginada, conteo de no leídas, marcar una/todas como leídas (solo las propias → 404 si es ajena). |
| `PreferenciaService` | Consultar y cambiar preferencias del usuario; por defecto todo habilitado. |
| `DispositivoService` | Registrar token push (reasignar si el token ya existe en otro usuario), desactivar al cerrar sesión. |
| `EmailSender` | Enviar correo por SMTP usando la plantilla de canal EMAIL. |
| `PushSender` (interfaz) + `LogPushSender` | MVP: solo registra el envío en log. Fase 2: implementación FCM. |
| `ReintentoScheduler` | Tarea periódica: toma notificaciones PENDIENTE/FALLIDA con menos de 3 intentos, vuelve a intentar (incluye re-resolver contactos si faltaban), registra cada intento en `intentos_envio`; al tercer fallo queda FALLIDA definitiva. |
| `EnvioManualService` | ADMIN envía un aviso a usuarios, o a todos los de un rol, por los canales elegidos. |

### 9.9 `controller/` — API (prefijo `/api/v1/notificaciones`)

| Endpoint | Debe hacer | Roles |
|---|---|---|
| `GET /mias` | Bandeja paginada (`?leida=`) | autenticado |
| `GET /mias/no-leidas/conteo` | Número para la insignia de la campana | autenticado |
| `PATCH /{id}/leida` | Marcar como leída | autenticado (solo propias) |
| `PATCH /mias/leer-todas` | Marcar todas | autenticado |
| `GET /preferencias`, `PUT /preferencias` | Ver y cambiar preferencias | autenticado |
| `POST /dispositivos`, `DELETE /dispositivos/{token}` | Registrar/baja de token push | autenticado |
| `GET /plantillas`, `PUT /plantillas/{id}` | Gestionar plantillas | ADMIN |
| `POST /enviar` | Envío manual | ADMIN |

Los clientes consultan la bandeja cada ~30 s (sin tiempo real en MVP).

### 9.10 `test/`
- Cada evento genera la notificación correcta para los destinatarios correctos.
- Mismo evento procesado dos veces → una sola notificación.
- Con `alumnos-ms` caído, el circuit breaker abre, la notificación queda PENDIENTE y el reintento posterior la resuelve.
- Un usuario no puede leer ni marcar notificaciones ajenas.
- Preferencia deshabilitada → no se crea notificación de ese canal.

---

## 10. Contratos de eventos Kafka

Formato: JSON, clave = id de la entidad. Cada servicio mantiene **su propia copia** del evento; este cuadro es el contrato.

### 10.1 Producidos por los servicios de esta entrega

| Tópico | Productor | Clave | Campos |
|---|---|---|---|
| `alumno.registrado` | alumnos-ms | alumnoId | alumnoId, codigo, nombreCompleto, fechaNacimiento, apoderadoIds[], usuarioIds[], fecha |
| `alumno.estado-cambiado` | alumnos-ms | alumnoId | alumnoId, estadoAnterior, estadoNuevo, motivo, fecha |
| `horario.publicado` | planificacion-ms | seccionId | seccionId, seccionNombre, anioId, version, docenteUsuarioIds[], fecha |
| `anio.activado` | planificacion-ms | anioId | anioId, nombre, fechaInicio, fechaFin |
| `periodo.cambiado` | planificacion-ms | periodoId | periodoId, numero, nombre, estado, anioId |

### 10.2 Consumidos por `notificaciones-ms`

| Tópico | Productor (otro integrante) | Campos mínimos que necesita | Plantilla |
|---|---|---|---|
| `matricula.creada` | matricula-ms | alumnoId, matriculaId, anio, gradoNombre, seccionNombre | MATRICULA_CREADA |
| `pago.confirmado` | pago-ms | alumnoId, pagoId, monto, concepto | PAGO_CONFIRMADO |
| `pago.vencido` | pago-ms | alumnoId, cuotaId, concepto, fechaVencimiento | PAGO_VENCIDO |
| `asistencia.falta-registrada` | asistencia-ms | alumnoId, asistenciaId, fecha | ASISTENCIA_FALTA |
| `tarea.publicada` | tareas-ms | tareaId, cursoNombre, titulo, fechaEntrega, alumnoIds[] | TAREA_PUBLICADA |
| `tarea.calificada` | tareas-ms | alumnoId, tareaId, titulo, nota | TAREA_CALIFICADA |
| `examen.calificado` | examenes-ms | alumnoId, examenId, cursoNombre, nota | EXAMEN_CALIFICADO |
| `boletin.generado` | calificaciones-ms | alumnoId, boletinId, periodoNombre | BOLETIN_GENERADO |
| `horario.publicado` | planificacion-ms | docenteUsuarioIds[], seccionNombre | (aviso a docentes) |

> **Acción para el equipo:** los nombres de tópicos usan punto y guion (`asistencia.falta-registrada`). Cambia respecto a la lista anterior (`asistencia.falta_registrada`); se unifica ahora para no migrar luego. Los eventos deben llevar **`alumnoId`** para que `notificaciones-ms` resuelva contactos; si algún integrante prefiere incluir directamente los `usuarioIds` destinatarios, también sirve y se evita la llamada Feign.

Entrega: **al menos una vez**; la idempotencia fina se trabaja en la S9 (por ahora la protege el índice único de `notificaciones`).

---

## 11. Rutas del Gateway y seguridad por rol

Rutas (id → destino → predicado):

| Id | Destino | Predicado |
|---|---|---|
| `auth-ms` | `lb://auth-ms` | `/api/v1/auth/**` |
| `planificacion-ms` | `lb://planificacion-ms` | `/api/v1/planificacion/**` |
| `alumnos-ms` | `lb://alumnos-ms` | `/api/v1/alumnos/**`, `/api/v1/apoderados/**` |
| `notificaciones-ms` | `lb://notificaciones-ms` | `/api/v1/notificaciones/**` |

Nunca se enrutan `/internal/**` ni `/actuator/**`.

Reglas de acceso (resumen):

| Ruta | Sin token | ALUMNO | APODERADO | DOCENTE | SECRETARIA | CAJERO | ADMIN |
|---|---|---|---|---|---|---|---|
| `POST /auth/login` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `/planificacion` (GET) | ✘ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `/planificacion` (escritura) | ✘ | ✘ | ✘ | ✘ | solo horarios/docentes/asignaciones | ✘ | ✔ |
| `/alumnos` lectura | ✘ | propio | hijos | ✔ | ✔ | ✘ | ✔ |
| `/alumnos` escritura | ✘ | ✘ | ✘ | ✘ | ✔ | ✘ | ✔ |
| `/notificaciones/mias…` | ✘ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `/notificaciones/plantillas`, `/enviar` | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✔ |

Seguridad en dos capas: el Gateway aplica el rol grueso por ruta; el servicio aplica la regla fina (ej. "solo mis hijos"). Sin token → 401; token válido sin rol → 403.

---

## 12. Bases de datos

Scripts en `eduspace-sql/` (validados contra PostgreSQL 16):

| Archivo | Base | Tablas |
|---|---|---|
| `00_crear_bases.sql` | — | Crea usuario y las 3 bases (para uso sin Docker) |
| `planificacion-ms/V1__init_planificacion.sql` | `planificacion_db` | anios_academicos, periodos, niveles, grados, aulas, secciones, cursos, grado_cursos, docentes, asignaciones, bloques_horarios, horarios, publicaciones_horario, feriados |
| `alumnos-ms/V1__init_alumnos.sql` | `alumnos_db` | alumnos, apoderados, alumno_apoderado, alumno_documentos, alumno_salud, alumno_estado_historial |
| `notificaciones-ms/V1__init_notificaciones.sql` | `notificaciones_db` | plantillas_notificacion, notificaciones, preferencias_notificacion, dispositivos_push, intentos_envio |

Reglas que la BD ya hace cumplir (el código las repite para dar mensajes claros):
- **planificacion:** un solo año ACTIVO; periodo único por año; sección única por año/grado/nombre; una asignación por sección y curso; una clase por asignación/día/bloque; fechas y capacidades coherentes.
- **alumnos:** documento único; código único; **un solo apoderado principal** y **un solo responsable de pago** por alumno; parentescos y estados restringidos.
- **notificaciones:** plantilla única por código/canal; **anti-duplicado** por (usuario, tipo, canal, evento, clave); índices parciales para bandeja no leída y pendientes.

Semillas: niveles (Inicial, Primaria, Secundaria), 14 grados (3 + 6 + 5) y 8 plantillas IN_APP.
Sin claves foráneas entre bases: `usuario_id` (auth-ms) se guarda como número simple.

**Qué NO está en estas bases (a propósito):** grado/sección del alumno (vive en `matricula_db`), tarifas (en `pago_db`), notas y asistencia (en sus servicios).

---

## 13. Orden de implementación (hasta S8)

1. **S1–S2:** repositorios, `config-server` + `config-repo`, `eureka-server`. Verificar que un servicio de prueba se registra.
2. **S3–S4:** `planificacion-ms` completo (CRUD + reglas + Flyway) y `alumnos-ms` completo; probar con Swagger y Postman.
3. **Gateway:** rutas hacia ambos servicios y filtro de `traceId`; probar a través del 18080.
4. **S6:** Feign + Resilience4j en `notificaciones-ms` hacia `alumnos-ms`; probar apagando `alumnos-ms`.
5. **S7:** `auth-ms` + seguridad en Gateway y servicios; probar 401/403 y roles.
6. **S8:** Kafka + Kafka UI; productores de `planificacion-ms` y `alumnos-ms`; consumidores de `notificaciones-ms`; probar punta a punta: crear alumno → ver notificación en bandeja.
7. **Observabilidad:** Prometheus/Loki/Grafana con los tres servicios.
8. **Dockerización PROD:** Dockerfile + compose de cada pieza en `eduspace-prod-net`.

Criterio de "terminado" por servicio: arranca con Config Server, se registra en Eureka, responde por el Gateway con token, publica/consume sus eventos y no tiene errores de Flyway.

---

## 14. Decisiones pendientes y riesgos

| # | Tema | Recomendación | Quién decide |
|---|---|---|---|
| 1 | Opción A (servicios separados) vs B (`registro-escolar-ms`) | A | Equipo |
| 2 | Un colegio vs multi-tenant | Un colegio; RLS como mejora futura | Equipo |
| 3 | Dueño de tarifas | `pago-ms` | Witmer |
| 4 | Formato de evento: ¿`alumnoId` o `usuarioIds` destinatarios? | `alumnoId` + Feign con circuit breaker | Hermes con cada productor |
| 5 | Alta de cuentas de usuario | Manual en MVP; automática por evento en fase 2 | Equipo |
| 6 | Llamadas `/internal/**` sin token | Aceptable en red Docker; endurecer en S10 | Equipo |
| 7 | Notificar horario a alumnos | Fase 2, cuando `matricula-ms` exponga la lista por sección | Hermes y Andre |
| 8 | Puertos de infraestructura iguales a pagatu | Ejecutar uno u otro, o cambiar el prefijo si se necesitan ambos | Equipo |

**Riesgos principales**
- `planificacion-ms` es dependencia de casi todos: mantener sus lecturas simples y paginadas; los consumidores deben usar caché y circuit breaker.
- Datos de menores (`alumnos-ms`): acceso por rol, sin datos sensibles en logs ni eventos (los eventos llevan ids y nombre, **no** salud ni documentos).
- Incompatibilidad de eventos entre integrantes: este documento es el contrato; cualquier cambio de campos se avisa y se versiona.
- Sin idempotencia completa hasta la S9: la protección actual solo cubre notificaciones.

---

### Pendiente fuera de esta entrega
- Rediseño de la diapositiva 6 (arquitectura) con 10 nodos y su base bajo cada servicio, y actualización del PPTX/PDF con la nueva división.
- Especificación archivo por archivo de los 7 servicios restantes (se hace con el mismo molde de §7–§9).
- Implementación del código (cuando se pida).
