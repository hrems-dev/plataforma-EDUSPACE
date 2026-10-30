# Instalación y ejecución

## Requisitos
| Herramienta | Versión | Para qué |
|---|---|---|
| Java | 21 | Servicios Spring Boot 4.1.1 / Spring Cloud 2025.1.3 |
| Maven | wrapper `./mvnw` incluido | Compilar |
| Node.js | 24 (npm) | Frontend Angular 22 |
| Podman o Docker | reciente | PostgreSQL 16, Kafka 4.3, MinIO, Mailpit, Keycloak 26 |
| Python 3 | biblioteca estándar | Escenarios e2e y semillas de demo |
| Memoria libre | ~9 GB | 13 JVM de 256 MB + contenedores |

## Puertos
| Pieza | Puerto | | Pieza | Puerto |
|---|---|---|---|---|
| Frontend (`ng serve`) | 4200 | | Config Server | 18888 |
| Gateway | 18080 | | Eureka | 18761 |
| Keycloak | 18090 | | Kafka | 19092 |
| auth-ms | 8090 | | MinIO / Mailpit | 19000 / 18025 |
| planificacion · alumnos · matricula | 8091 · 8092 · 8093 | | Prometheus / Grafana / Loki | 19090 / 13000 / 13100 |
| asistencia · calificaciones · pago | 8094 · 8095 · 8096 | | PostgreSQL por servicio | 15440–15449 |
| examenes · tareas · notificaciones | 8097 · 8098 · 8099 | | Kafka UI | 18085 |

## Levantar todo en local (recomendado)
```bash
tools/e2e/compilar.sh            # una vez, o tras cambiar código
tools/e2e/levantar.sh            # recrea bases y arranca todo (~2 min). Keycloak es el emisor por defecto
tools/e2e/levantar.sh --sin-reset  # conserva los datos (tras reiniciar el equipo: podman start de los contenedores e2e-* y Keycloak)
tools/e2e/levantar.sh --auth-propio  # los servicios confían en auth-ms (necesario para los escenarios e2e)
tools/e2e/detener.sh
```
Comprobar: `for p in 8090 8091 8092 8093 8094 8095 8096 8097 8098 8099 18080; do curl -s localhost:$p/actuator/health | head -c 40; echo; done`

## Datos de demostración
```bash
python3 tools/demo/semilla.py todo          # año, secciones, cursos, docentes, alumnos listos para matricular
python3 tools/demo/semilla.py matriculas    # solo secciones, tarifas y familias sin matrícula
python3 tools/demo/semilla.py docentes      # cursos de Primaria y docentes con especialidad
```

## Frontend
```bash
cd frontend && npm install
npm start                 # modo keycloak (por defecto): http://localhost:4200
npm run auth:eduspace     # modo login propio contra auth-ms
npx ng test --watch=false # 55 pruebas
npx ng build              # compilación de producción
```

## Cada servicio por separado
```bash
cd services/planificacion-ms && ./mvnw spring-boot:run        # con Config Server + Eureka
# modo aislado (sin Config Server/Eureka): ver README.md raíz, sección «Cómo correrlo (DEV)»
```
Swagger: `http://localhost:8091/swagger-ui.html` (y 8092, 8099…). Los endpoints `/internal/**` no se enrutan por el Gateway.

## Usuarios de prueba (solo DEV, localhost)
Roles `ADMIN`, `SECRETARIA`, `CAJERO`, `DOCENTE`, `APODERADO`, `ALUMNO`. Los nombres y claves de desarrollo están en `infra/keycloak/README.md`
y `tools/e2e/README.md`; **no se reutilizan en PROD** (ver [Seguridad](seguridad.md)).

## Correo de pruebas
En DEV todos los correos (incluida la constancia de la matrícula en línea) llegan a **Mailpit**: `http://localhost:18025`. Para correo real, configurar en
`notificaciones-ms`: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM`.

## Observabilidad opcional
```bash
cd obs && docker compose -f compose-dev.yml up -d     # Prometheus, Loki, Promtail, Grafana
```
