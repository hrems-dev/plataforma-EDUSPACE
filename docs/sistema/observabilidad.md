# Observabilidad

Según la sesión S3/S4 de Pagatu y el criterio «logs, health checks, métricas y paneles» del producto final.

| Señal | Cómo | Estado |
|---|---|---|
| **Salud** | `/actuator/health` (liveness/readiness) en los 13 procesos; el Gateway y Eureka lo usan | Medido: 13/13 UP, 11 aplicaciones registradas en Eureka |
| **Métricas** | `/actuator/prometheus` (Micrometer); Prometheus descubre los servicios por **Eureka** (`eureka_sd_configs`) | Disponible (`obs/prometheus`) |
| **Logs** | Archivos por servicio, recogidos por **Promtail → Loki**; formato con `traceId` | Disponible |
| **Trazas** | `X-Trace-ID` generado por el frontend o el Gateway y propagado (`CorrelationIdFilter`) y presente en los eventos (`traceId`) | Disponible |
| **Paneles** | Grafana (`:13000`) con el panel `eduspace-infra.json` provisionado | Panel base; falta un panel por servicio |
| **Eventos** | Kafka UI (`:18085`), lag por grupo | Lag 0 en los 7 grupos medido el 2026-10-29 |
| **Correo de prueba** | Mailpit (`:18025`) | Disponible |

## Comandos útiles
```bash
curl -s localhost:8091/actuator/health
curl -s localhost:18761/eureka/apps -H 'Accept: application/json'      # instancias registradas
podman exec e2e-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:19092 --describe --all-groups
tail -f tools/e2e/logs/planificacion-ms.log
```

## Exposición de Actuator
Los servicios exponen solo `health,info,prometheus`. En PROD el Gateway solo deja pasar `health` e `info` sin token.

## Alertas del negocio (dentro del sistema)
- `pago-ms`: `alertas_admin` (falta tarifa, cuota parcial al retirar).
- `asistencia-ms`: alerta por exceso de faltas en el reporte de la sección.
- `notificaciones-ms`: reintentos (`intentos_envio`) y fila marcadora si alumnos-ms no responde.
