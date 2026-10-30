# Informe de evidencias — eduspace-completo

Generado: 2026-10-29 18:44:54 · hrems-dev@fedora

Resumen: 0 de 0 comprobaciones ejecutadas con resultado correcto.

## Unidad 1 — Sistema distribuido base orientado a producción

Producto: Sistema distribuido base funcional, configurable y preparado para múltiples instancias.

### S1 — Construcción de un servicio base

Criterios de la rúbrica:
- Microservicios delimitados por dominio
- Persistencia con PostgreSQL y Flyway
- Endpoints REST funcionales y documentados
- Ejecución y escalamiento horizontal
- Documentación técnica reproducible

**Estado de los servicios (actuator/health)** — sin ejecutar

**Documentación Swagger / OpenAPI** — sin ejecutar

### S2 — Configuración centralizada y ambientes

Criterios de la rúbrica:
- Config Server operativo
- Configuración externa por servicio
- Servicio como Config Client
- Comparación DEV/PROD

**Config Server: configuración DEV y PROD** — sin ejecutar

### S3 — Registro, descubrimiento y ejecución concurrente

Criterios de la rúbrica:
- Eureka operativo
- Servicio migrado a Config Client
- Múltiples instancias registradas
- Comprensión del patrón

**Registro de servicios e instancias (Eureka)** — sin ejecutar

### S4 — Punto único de acceso y distribución de tráfico

Criterios de la rúbrica:
- Gateway operativo
- Rutas hacia los servicios
- Ruta nueva y balanceo de carga
- Comprensión del patrón

**Gateway: rutas y balanceo entre instancias** — sin ejecutar

### S5 — Evaluación de la Unidad I

Criterios de la rúbrica:
- Configuración por ambiente verificable
- Registro y balanceo con trazabilidad en logs
- Sustentación del sistema

**Estado de los servicios (actuator/health)** — sin ejecutar

**Config Server: configuración DEV y PROD** — sin ejecutar

**Registro de servicios e instancias (Eureka)** — sin ejecutar

**Gateway: rutas y balanceo entre instancias** — sin ejecutar

## Unidad 2 — Sistema distribuido robusto

Producto: Sistema distribuido seguro, resiliente, consistente, observable e integrado con cliente frontend.

### S6 — Comunicación síncrona resiliente

Criterios de la rúbrica:
- Servicio cliente construido
- Comunicación Feign
- Circuit Breaker
- Contrato y datos
- Observabilidad

**Circuit Breakers y resiliencia** — sin ejecutar

### S7 — Seguridad distribuida y control de acceso

Criterios de la rúbrica:
- Servicio de autenticación (JWT)
- Gateway como Resource Server
- Servicios validan el JWT
- Matriz de roles y accesos

**Seguridad: rutas protegidas y JWKS** — sin ejecutar

### S8 — Mensajería asíncrona entre servicios

Criterios de la rúbrica:
- Kafka y servicio de pagos
- Eventos entre orden y pago
- Desacople evidenciado
- Evento de negocio adicional
- Contrato de los eventos

**Kafka: contenedor y tópicos** — sin ejecutar

### S9 — Consistencia distribuida en procesos de negocio

Criterios de la rúbrica:
- Idempotencia
- Fallo de pago y compensación
- Compensación idempotente
- Diagrama de estados

> Se prueba publicando eventos duplicados y fallidos (ver evidencias/generar_evidencias.sh del curso); aquí no hay comprobación automática.

### S10 — Observabilidad y diagnóstico

Criterios de la rúbrica:
- Health checks de los servicios
- Métricas (Prometheus)
- Logs centralizados (Loki)
- Correlación entre servicios ante un fallo

**Observabilidad: health, Prometheus, Loki y Grafana** — sin ejecutar

### S11 — Integración con cliente frontend

Criterios de la rúbrica:
- Frontend sirve y llama por el Gateway
- Seguridad y consumo de APIs protegidas

**Frontend y llamadas por el Gateway** — sin ejecutar

### S12 — Evaluación de la Unidad II

Criterios de la rúbrica:
- Resiliencia
- Seguridad
- Mensajería
- Consistencia
- Observabilidad
- Frontend

**Circuit Breakers y resiliencia** — sin ejecutar

**Seguridad: rutas protegidas y JWKS** — sin ejecutar

**Kafka: contenedor y tópicos** — sin ejecutar

**Observabilidad: health, Prometheus, Loki y Grafana** — sin ejecutar

**Frontend y llamadas por el Gateway** — sin ejecutar

## Unidad 3 — Validación y consolidación del producto del curso

Producto: Sistema de microservicios end-to-end, validado, documentado, estabilizado y defendido técnicamente.

### S13 — Validación end-to-end

Criterios de la rúbrica:
- Flujo principal completo
- Al menos un caso de error

**Registro de servicios e instancias (Eureka)** — sin ejecutar

**Gateway: rutas y balanceo entre instancias** — sin ejecutar

**Seguridad: rutas protegidas y JWKS** — sin ejecutar

**Observabilidad: health, Prometheus, Loki y Grafana** — sin ejecutar

### S14 — Revisión técnica y estabilización

Criterios de la rúbrica:
- Documentación y evidencias
- Configuración y despliegue local

> Revisión de documentación, repositorio y guion de demostración; sin comprobación automática.

### S15 — Evaluación de la Unidad III

Criterios de la rúbrica:
- Demostración en vivo del flujo end-to-end
- Evidencia de validación con un caso de error
- Aporte individual y commits

**Estado de los servicios (actuator/health)** — sin ejecutar

**Config Server: configuración DEV y PROD** — sin ejecutar

**Registro de servicios e instancias (Eureka)** — sin ejecutar

**Gateway: rutas y balanceo entre instancias** — sin ejecutar

**Seguridad: rutas protegidas y JWKS** — sin ejecutar

**Kafka: contenedor y tópicos** — sin ejecutar

**Observabilidad: health, Prometheus, Loki y Grafana** — sin ejecutar

### S16 — Continuación de la evaluación de la Unidad III

Criterios de la rúbrica:
- Misma evaluación de S15 para quien no la rindió

**Registro de servicios e instancias (Eureka)** — sin ejecutar

**Gateway: rutas y balanceo entre instancias** — sin ejecutar

**Observabilidad: health, Prometheus, Loki y Grafana** — sin ejecutar
