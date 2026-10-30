# Pruebas y métricas

Medidas el **2026-10-29** sobre el entorno local (13 JVM con `-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`, PostgreSQL y Kafka en Podman). Los valores son de una máquina de desarrollo, no de producción.

## 1. Tamaño del sistema

| Servicio | Archivos Java | Líneas (main) | Líneas (test) | Pruebas `@Test` | Endpoints | Migraciones Flyway |
|---|---|---|---|---|---|---|
| auth-ms | 26 | 1 316 | 294 | 15 | 15 | 1 |
| planificacion-ms | 146 | 5 081 | 1 059 | 48 | 86 | 3 |
| alumnos-ms | 100 | 3 191 | 955 | 38 | 32 | 2 |
| matricula-ms | 55 | 2 785 | 457 | 25 | 29 | 2 |
| asistencia-ms | 54 | 2 620 | 404 | 21 | 25 | 2 |
| calificaciones-ms | 83 | 4 620 | 376 | 16 | 29 | 2 |
| pago-ms | 101 | 4 935 | 558 | 26 | 32 | 2 |
| examenes-ms | 69 | 3 638 | 443 | 21 | 26 | 2 |
| tareas-ms | 53 | 2 698 | 394 | 21 | 25 | 2 |
| notificaciones-ms | 97 | 2 956 | 856 | 37 | 17 | 4 |
| **Total servicios** | **784** | **33 840** | **5 796** | **268** | **316** | **22** |
| Gateway / Config / Eureka | — | 180 / 15 / 15 | — | 11 / 0 / 0 | — | — |

| Frontend | Valor |
|---|---|
| TypeScript (sin specs) | 15 531 líneas |
| Plantillas HTML / CSS de componentes | 4 713 / 1 687 líneas |
| Componentes / servicios | 82 / 18 |
| Rutas con carga perezosa | 66 |
| Pruebas (Vitest) | 62 en 11 archivos |
| Carga inicial | 384 kB (105 kB transferidos); `styles` 15 kB |
| Salida de producción | 1,5 MB, 98 archivos JS |

## 2. Pruebas
| Nivel | Qué | Resultado |
|---|---|---|
| Unitarias y de integración (servicios) | PostgreSQL real con Testcontainers; vecinos y Kafka simulados | Última corrida completa (2026-10-16): **255 en verde**. Hoy el recuento es de ~268 `@Test`; las de integración exigen Docker/Testcontainers y **no corren con Podman sin socket configurado** (ver `README.md`); las unitarias de planificacion (26) pasan |
| Frontend (Vitest) | rutas, menú, guards, interceptor, espacios de trabajo | **62/62** (2026-10-29) |
| Compilación | `ng build` | OK, sin advertencias |
| Punta a punta por consola | 6 escenarios (`tools/e2e`) | Todos OK en la última corrida (2026-10-23) |
| Auditoría de pantallas (2026-10-29) | Recorrido automático de **130 páginas** (admin, secretaría, caja, docente, alumno, apoderado y sitio público) en escritorio y en celular (390 px): errores de consola, respuestas 4xx/5xx, páginas vacías, textos `undefined`/`NaN`, redirecciones y desborde horizontal; revisión visual de cada captura | 0 problemas (el único aviso es el 404 esperado de «ficha de salud sin registrar») |
| Navegador real | Playwright con Chrome sobre todos los roles y los flujos nuevos (matricular, trasladar, crear/eliminar sección, avisos, armado de horarios) | Sin errores de consola ni 4xx/5xx |
| Gateway | token ausente, firma ajena o alterada, vencido, actuator cerrado, límite 429 | 11 pruebas |

## 3. Rendimiento medido
**Latencia por el Gateway** (30 peticiones secuenciales por endpoint, usuario ADMIN, datos de demo; incluye Gateway + validación de JWT + servicio + base):

| Consulta | Mediana | p95 | Máximo |
|---|---|---|---|
| `GET planificacion/anios/activo` | 9,8 ms | 15,2 ms | 52 ms |
| `GET planificacion/secciones?anioId=1` (25 secciones) | 25,7 ms | 34,7 ms | 124 ms |
| `GET planificacion/horarios/seccion/1` | 22,9 ms | 32,4 ms | 42 ms |
| `GET planificacion/horarios/anio/1` (288 clases) | 60,3 ms | 117,6 ms | 208 ms |
| `GET alumnos?page=0&size=15` | 17,8 ms | 32,4 ms | 133 ms |
| `GET matriculas?page=0&size=15` | 14,9 ms | 27,3 ms | 95 ms |
| `GET matriculas/cupos?anioId=1` | 66,3 ms | 104,7 ms | 146 ms |
| `GET pagos/cuotas?page=0&size=15` | 17,7 ms | 32,6 ms | 184 ms |
| `GET notificaciones/mias` | 9,9 ms | 59,0 ms | 549 ms |
| `GET asistencia/secciones/1/reporte` | 14,6 ms | 22,0 ms | 32 ms |

`matriculas/cupos` es la consulta más lenta entre las rutinarias (66 ms): recorre todas las secciones del año; candidata a caché si crece.

**Armado automático de horarios** (Primaria, 12 secciones, 288 clases a colocar; 3 corridas de simulación): **0,63 s, 0,39 s, 0,28 s** — 288/288 horas colocadas, 0 sin colocar.

**Memoria (RSS) por proceso:** auth 80 MB · planificacion 111 · alumnos 91 · matricula 124 · asistencia 101 · calificaciones 150 · pago 123 · examenes 100 · tareas 118 · notificaciones 173 · gateway 94 · config 104 · eureka 84 → **~1,45 GB** los 13 procesos Java.

**Arranque** (los 10 servicios en paralelo, JVM limitada): Config 4,8 s · Eureka 7,7 s · auth 14,4 s · Gateway 19,8 s · servicios 43–60 s (arrancan a la vez y comparten CPU; en solitario es menor).

**Límites del Gateway** (configuración, probados): API 300 peticiones/min por IP con ráfaga 50; login 10/min con ráfaga 5.

## 4. Estado de datos y mensajería (2026-10-29)
| Métrica | Valor |
|---|---|
| Procesos UP | 13 / 13; 11 aplicaciones registradas en Eureka |
| Alumnos / matrículas | 29 alumnos; matrículas: 3 MATRICULADO, 1 PRE_MATRICULA, 2 ANULADO |
| Secciones / clases de horario | 25 / 288 (Primaria) |
| Tópicos Kafka / colas DLT | 24 / 18 |
| Lag de consumidores | 0 en 7 grupos (asistencia 8 particiones, calificaciones 9, examenes 8, matricula 4, notificaciones 24, pago 8, tareas 8) |
| Outbox pendiente | 0 en planificacion (3), alumnos (29), matricula (12) y pago (29) |
| Tamaño de las bases | 7,9–9,1 MB cada una (~84 MB en total) |

## 5. Cómo repetir las mediciones
```bash
# latencias: guion con token de DEV y 30 peticiones por endpoint (4–5 por segundo para no activar el 429)
# memoria:   ps -o rss= -p $(ss -ltnp | grep ':8091 ' | grep -oP 'pid=\K[0-9]+')
# arranque:  grep -h "Started .* in" tools/e2e/logs/*.log
# tamaño:    cloc/wc sobre services/*/src ; frontend: npx ng build
# lag Kafka: kafka-consumer-groups.sh --describe --all-groups
```

## 6. Qué falta medir
Pruebas de carga (concurrencia, 1 000 matrículas simultáneas), rendimiento en PROD, cobertura de código (JaCoCo) y pruebas de resiliencia con caída de servicios en frío. Ver [Limitaciones](limitaciones.md).
