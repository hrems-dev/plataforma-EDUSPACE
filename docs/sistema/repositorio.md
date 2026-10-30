# Repositorio y estándares

## Estructura
```
eduspace-completo/
├── infra/       # eduspace-config, eduspace-eureka, eduspace-gateway, keycloak
├── kafka/       # Kafka + Kafka UI + MinIO (compose-dev.yml / compose.yml)
├── obs/         # Prometheus, Loki, Promtail, Grafana
├── services/    # 10 proyectos Spring Boot: pom, mvnw, Dockerfile, compose(-dev).yml, src
├── frontend/    # Angular 22
├── tools/       # e2e/ (levantar, escenarios) y demo/ (semillas de datos)
├── sql/         # V1 de referencia y 00_crear_bases.sql
├── docs/        # informes, bitácora, verificación y esta documentación
├── README.md · PENDIENTES.md · INFORME-ESTADO.md
└── mkdocs.yml   # publica docs/sistema (ver abajo)
```

## Estándares
- Patrón Pagatu: `controller → service → repository`, DTO/mapper, `GlobalExceptionHandler`, Flyway con `validate`, un `Dockerfile` y un compose por servicio.
- Configuración en el Config Server, `application.yml` mínimo.
- Eventos: sobre estándar, outbox, `eventos_procesados`, DLT.
- Pruebas: PostgreSQL real con Testcontainers para integración; escenarios e2e por consola; Vitest en el frontend.
- Commits en español, en imperativo, con alcance claro; cada commit deja compilando y con pruebas en verde.
- Documentación: este conjunto en `docs/sistema/` + un documento por persona en `docs/equipo/`.

## Historial
`git log --oneline` — ver la rama `main`. Commits recientes: avance hasta el front; menú por espacios de trabajo, armado automático y avisos; documentación por persona.

## Publicar esta documentación (MkDocs, como Pagatu)
```bash
pip install mkdocs-material
mkdocs serve        # http://127.0.0.1:8000
mkdocs build        # genera site/
```
El `mkdocs.yml` de la raíz usa `docs_dir: docs` y el tema *material*, con diagramas Mermaid.
> Topics académicos del repositorio (Pagatu §Repositorio): por definir con el docente al publicarlo.
