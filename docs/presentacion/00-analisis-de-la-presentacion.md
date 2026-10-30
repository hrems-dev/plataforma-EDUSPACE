# Análisis: cómo presentar EduSpace en clase

## 1. Qué pide la clase
La guía del Proyecto Sello (Pagatu, `class/pagatu/docs/proyecto-sello/u3-producto.md`) pide un **sistema distribuido end-to-end, validado, documentado, estabilizado y defendido técnicamente**.
Lo que se evalúa (y por tanto lo que hay que mostrar) es:

| Criterio de la guía | Dónde lo demuestra EduSpace |
|---|---|
| Microservicios con responsabilidades claras | 10 servicios, dueño de datos por servicio (`docs/equipo/README.md`) |
| Configuración centralizada y registro/descubrimiento | Config Server + Eureka + Gateway `lb://` |
| Comunicación síncrona resiliente | Feign solo a `/internal/**` con token de servicio, circuit breaker, caché |
| **Seguridad distribuida** | JWT RS256/Keycloak, roles, `usuarioId`, límite por IP, rutas públicas controladas |
| **Mensajería asíncrona** | Kafka: 25 tópicos, outbox, DLT |
| **Consistencia distribuida (transaccional, compensación, idempotencia)** | **Un proceso transaccional por integrante** (ver `01-procesos-transaccionales.md`) |
| Observabilidad | Health, Prometheus/Grafana, `X-Trace-ID`, lag de Kafka |
| Frontend integrado por el Gateway | Portal Angular por rol + sitio público con matrícula en línea |
| Entorno reproducible y documentación | `tools/e2e/levantar.sh`, MkDocs (`docs/sistema/`), métricas medidas |
| Aporte individual verificable | Un documento por persona (`docs/equipo/`) + su proceso transaccional |

## 2. Quién presenta qué (y por qué)
Cada integrante defiende **sus tres servicios** y **un proceso transaccional** que cruza su parte con las de los demás.

| Orden | Quién | Bloque | Proceso transaccional | Por qué ese |
|---|---|---|---|---|
| 1 | **Todos** (el que modere) | Problema, arquitectura, demo de plataforma | — | Contexto común (3 min) |
| 2 | **Hermes** | planificación · alumnos · notificaciones (+ auth) | **Publicar un horario «todo o nada»**: generador con rollback, invariantes, versión + evento en una transacción | Es la fuente de verdad: si su dato se publica mal, todos lo sufren |
| 3 | **Andre Fabricio** | matrícula · asistencia · tareas | **Matrícula con cupo atómico, pago y compensación** (saga coreografiada) | Es el proceso de negocio más largo y el que une a más servicios |
| 4 | **Witmer** | pago · exámenes · calificaciones | **Cobro idempotente con comprobante y evento, y su anulación** | Es donde un error cuesta dinero: idempotencia y exactamente-una-vez |
| 5 | **Todos** | Flujo integrado (matrícula en línea), seguridad, métricas, cierre | Recorrido de punta a punta | Muestra que las tres partes encajan |

Reglas del reparto: cada uno habla **8–10 min**; quien presenta un servicio **debe poder abrir su código y explicarlo línea a línea** (por eso hay una guía de código en `03-ensenanza-de-codigo.md`).
Si el docente pregunta por una parte ajena, la respuesta tiene que existir en `docs/equipo/` (todos deben haber leído los tres documentos).

## 3. Narrativa (el hilo que une todo)
> «Una familia matricula a su hijo desde la web. En segundos se reserva una vacante, se cobra, se confirma la matrícula, se crean las cuentas, se generan las pensiones,
> el hijo aparece en asistencia, tareas, exámenes y notas, y la familia recibe un correo con el horario. Ningún servicio lee la base de otro: todo ocurre por **transacciones
> locales + eventos**. Si algo falla en medio, el sistema **compensa**.»

Esa frase abre y cierra la exposición. Cada integrante muestra su tramo del hilo con una **transacción local real** y un **evento**.

## 4. Tiempos (45 min; para 10 minutos usa la [demostración rápida](05-demo-rapida.md))
| Min | Quién | Qué |
|---|---|---|
| 0–3 | Moderador | Problema y solución, cifras clave (10 servicios, ~316 endpoints, 25 tópicos) |
| 3–6 | Moderador | Arquitectura (diagrama), plataforma levantada, Eureka y Gateway en vivo |
| 6–15 | Hermes | Su bloque + **demo del proceso de horarios** |
| 15–24 | Andre | Su bloque + **demo de la saga de matrícula** |
| 24–33 | Witmer | Su bloque + **demo del cobro idempotente** |
| 33–40 | Todos | Matrícula en línea en el navegador (correo en Mailpit), seguridad y métricas |
| 40–45 | Todos | Limitaciones, trabajo futuro, preguntas |

## 5. Preparación (la víspera)
1. `tools/e2e/levantar.sh --auth-propio` y `npm run auth:eduspace` (cuentas del colegio); esperar ~2 min; comprobar `/actuator/health` de los 10 servicios.
2. Datos: `python3 tools/demo/semilla.py todo` (si la base está vacía).
3. Mailpit abierto en `http://localhost:18025`; Kafka UI en `:18085`; Grafana en `:13000` (opcional).
4. Ensayar **una vez** cada script de demo: `python3 tools/demo/proceso_hermes.py`, `proceso_andre.py`, `proceso_witmer.py`.
5. Pestañas del navegador ya abiertas: sitio público, portal (admin), Mailpit, Swagger de un servicio.
6. Plan B por si falla la red o un servicio: capturas en `docs/presentacion/capturas/` (tomarlas en el ensayo) y los resultados de los scripts guardados en texto.

## 6. Riesgos y qué responder
| Riesgo | Respuesta preparada |
|---|---|
| «¿Y si Kafka se cae?» | Outbox: el evento queda en la tabla y se reenvía; no se pierde (se enseña la tabla `outbox_eventos`) |
| «¿Y si llega dos veces el mismo mensaje?» | `eventos_procesados` + restricciones únicas; el cobro usa `idempotency_key` |
| «¿Por qué no un 2PC?» | Servicios con base propia y desacoplados; se usa saga coreografiada con compensaciones |
| «¿Qué pasa si se reinicia un servicio con sesión abierta?» | Clave de firma persistente en DEV; la web avisa y manda al login; franja de «servicio caído» |
| «¿La pasarela de pago es real?» | No: simulada y declarada; el punto de integración es `POST /internal/pagos/matricula-en-linea` |
| «¿Tests?» | ~280 pruebas en backend, 65 en frontend, 6 escenarios e2e, auditoría de 130 pantallas |

## 7. Entregables de la presentación
| Archivo | Para qué |
|---|---|
| `00-analisis-de-la-presentacion.md` | Este análisis |
| `01-procesos-transaccionales.md` | El proceso transaccional de cada integrante: pasos, tablas, eventos, compensación, código y demo |
| `02-guion-de-la-presentacion.md` | Guion minuto a minuto, qué dice y qué ejecuta cada uno, preguntas de defensa |
| `03-ensenanza-de-codigo.md` | Recorrido del código para enseñarlo (archivos, líneas y qué decir) |
| `04-diapositivas-para-generador.md` | Especificación diapositiva por diapositiva lista para pegar en un generador de presentaciones |
| `tools/demo/proceso_hermes.py`, `proceso_andre.py`, `proceso_witmer.py` | Demos ejecutables de cada proceso |
