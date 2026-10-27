# EduSpace — Pendientes (2026-10-16)

## Hecho
Ver `INFORME-ESTADO.md` y `docs/05-integracion-y-correcciones.md`. De la ronda anterior (Hermes): patrón Outbox, DLT,
aforo del aula, importación CSV, ficha de salud cerrada por defecto.

## Hecho el 2026-10-23
- Gateway valida el JWT (RS256 vía JWKS de auth-ms), con CORS (`CORS_ALLOWED_ORIGINS`) y límite multipart; solo login/refresh/logout y actuator pasan sin token. Verificado levantado en la e2e (2026-10-23): token ausente o inválido → 401, preflight CORS OK, origen ajeno → 403.
- DLT (`<tópico>.DLT`) y `ErrorHandlingDeserializer` en pago, asistencia, tareas, examenes, calificaciones y matricula (matricula en realidad no tenía DLT).
- Zona horaria (C11): ya estaba en `config-repo` para los tres servicios de Hermes; el hallazgo era falso.
- Literal por nivel: se resuelve el código del nivel con `planificacion-ms`. Solo existe la escala PRIMARIA en la BD: los demás niveles caen a ella hasta que se definan sus escalas (decisión de producto).
- Boletín por alumno: elige la matrícula de la sección/año pedidos.
- Banco de preguntas: actualizar/eliminar exigen ser el docente dueño (403 si no).
- 125 pruebas de los 6 servicios tocados pasan y los 6 escenarios e2e dan OK con el Gateway nuevo.

- `GET /api/v1/planificacion/docentes/mi` (planificacion-ms): el docente de la sesión por el claim `usuarioId`; lo usa el launcher "Horario EduSpace" (modo docente). Con prueba de integración.
- `POST /api/v1/pagos/online` ahora verifica que el alumno sea hijo del apoderado y que todas las cuotas sean de ese alumno (403 si no). Antes cualquier APODERADO podía pagar cuotas de cualquier alumno. Prueba nueva en `PagoIntegrationTest` (24 pruebas).

## Hecho el 2026-10-26 (revisión de seguridad contra la guía de pagatu)
- `/internal/**` exige el header `X-Service-Token` (token compartido `eduspace.service-token`, comparación en tiempo constante) en alumnos, planificacion, matricula y pago; asistencia, calificaciones, examenes y tareas lo deniegan (`denyAll`). Los clientes Feign de los 7 servicios que llaman lo envían solo hacia `/internal/`. DEV: `SERVICE_TOKEN` con valor de desarrollo; PROD: obligatorio, sin valor por defecto (el servicio no arranca sin él). Pruebas 401 sin token / token erróneo en los 4 proveedores.
- Gateway: Dockerfile, `compose.yml` (único servicio publicado hacia fuera) y `eduspace-gateway-prod.yml` (CORS y JWKS obligatorios, `forward-headers-strategy`, sin detalles de salud).
- Gateway: límite de peticiones por IP (`RateLimitFilter`): API 300/min con ráfaga 50; login/refresh 10/min con ráfaga 5; responde 429 con `Retry-After`. Por réplica y en memoria.
- Gateway: solo `/actuator/health` e `info` sin token; el resto de actuator solo con `actuator-public: true` (DEV). Los 7 servicios que no limitaban actuator en PROD ahora exponen solo `health,info,prometheus`.
- Pruebas nuevas del Gateway (10): token ausente, firmado por otra clave, firma alterada y vencido dan 401; token válido pasa; actuator cerrado; rate limit.
- `kafka/compose.yml` (PROD): red `eduspace-prod-net` (antes `pagatu-prod-net`), host `kafka` (el que usan los servicios) y puertos de Kafka/Kafka UI solo en `127.0.0.1`.

- `pago-ms`: desactivar una tarifa con monto redondo (150) daba 409 porque se comparaba el monto con `equals` (150 ≠ 150.00); ahora `compareTo`, con prueba.

- Keycloak (DEV) como emisor alterno: `infra/keycloak/` (realm `eduspace`, clientes `eduspace-web` con PKCE y `eduspace-e2e`, usuarios con atributo `usuarioId`) y modo `keycloak` en el front. Verificado contra el sistema real: 13/13 comprobaciones por el Gateway (401 sin token y con firma alterada, 403 por rol, claim `usuarioId` en pago-ms y planificacion-ms, token de auth-ms rechazado), flujo código+PKCE completo, refresh con rotación y reuso rechazado, redirect_uri ajena y *password grant* rechazados, bloqueo por fuerza bruta.

## Pendiente

| Pendiente | Motivo / qué lo destraba |
|---|---|
| `auth-ms`: PROD y extras | Claves RSA persistentes y claves de semillas fuera del repositorio; validación del JWT en el Gateway y CORS; endpoint para que `notificaciones-ms` envíe "a todos los de un rol" (hoy `GET /api/v1/auth/usuarios?rol=` ya lista por rol); recuperación de clave por correo. |
| Front | Hecho el 2026-10-26: panel ADMIN/SECRETARIA en `frontend/` (login, usuarios y roles, planificación, cobranza). Alumnos y matrícula (lista, expediente, documentos, apoderados, salud, traslado, retiro, ratificación, campañas, cupos) también hechos el 2026-10-27. Faltan horarios, reportes e importación CSV, y las apps de docente, caja y apoderado/alumno. |
| Consumidor de `tarea.entregada` | Nadie lo consume aún (útil para avisar al docente). |
| Salud: "solo tutor y docentes de la sección" | Ahora existe `/internal/matriculas/alumno/{id}` en matricula-ms; falta que alumnos-ms lo consulte. |
| Apoderado sin cuenta (correo/SMS directo) | Requiere cambiar `notificaciones.usuario_id` (NOT NULL) y la fila marcadora; definir canal y consentimiento. |
| FCM / SMS / WhatsApp | Credenciales y proveedor por decidir (hoy `LogPushSender`). |
| Ratificación: "aprobó el año" | En el MVP lo decide secretaría; luego consultar `resultados_anuales` de calificaciones-ms. |
| Unificar paquetes | `pe.edu.eduspace` (Hermes) vs `pe.edu.upeu.eduspace` (Witmer/Andre). |
| PROD en `eduspace-prod-net` y pruebas de carga | Entorno real necesario. |
| Cifrado en reposo de salud, soft-delete, derechos ARCO | Decisión legal/de producto (Ley 29733). |
| Auditoría general de cambios | Mejor como `auditoria-ms` que consuma eventos. |

## Hallazgos de la verificación del 2026-10-16 (detalle en `docs/07-verificacion-plan-y-pagatu.md`)

| Pendiente | Detalle |
|---|---|
| Circuit breaker propio (C5) | calificaciones-ms y examenes-ms usan solo fallback de Feign. |
| `fail-fast` del Config Server en PROD (C10) | Hoy todos usan `optional:`. |
| Token de servicio en `/internal/**` (C14) | Hecho el 2026-10-26 (ver arriba). Falta rotarlo y guardarlo como secreto en PROD; a futuro, mTLS o token firmado por servicio. |
| Verificar C8 y C16 | URL firmada de MinIO en tareas/alumnos; limpieza de tablas técnicas en todos. |
| Consumidores de eventos sin uso | `alumno.registrado`, `alumno.estado-cambiado`, `anio.activado`, `periodo.cambiado`. |
| Observabilidad (S10) | Un panel de Grafana por servicio (hoy solo el stack base). |
| Alinear con pagatu | `application.yml` mínimo (config al Config Server), un solo paquete base, mappers MapStruct reales en Andre/Witmer, estilo único de autorización, `mkdocs`/`uso-rapido`/`CLAUDE.md` en la raíz. |
| Fase 2 del plan | H14 recuperación y orden de mérito, H15 panel del director, H16 pasarela/SUNAT, H17 alta automática de cuentas. |

## Seguridad: lo que sigue abierto (revisión 2026-10-26)
| Pendiente | Detalle |
|---|---|
| Cuentas semilla | La migración V1 de `auth-ms` crea `admin`, `secretaria` y `cajero` con `debe_cambiar_clave = FALSE`; en PROD deben forzar el cambio o no crearse. |
| Contraseña por defecto en compose PROD | `auth-ms/compose.yml` y otros usan `eduspace_prod_secret` como valor de respaldo para la base. |
| Sin 2FA, sin `aud` en el JWT, sin revocación de access tokens | El access token dura 30 minutos aunque se haga logout. |
| Actuator y Swagger en los servicios | `/actuator/**` y Swagger siguen en `permitAll` en cada servicio; en PROD solo se limitó la exposición. Cerrar a la red de observabilidad. |
| Rate limit distribuido | El actual es por réplica del Gateway; con varias réplicas, usar Redis. |

## Keycloak: lo que queda (2026-10-27)
| Pendiente | Detalle |
|---|---|
| Usuarios en un solo lugar | Con Keycloak, `auth-ms` debería administrar los usuarios por la Admin API (cuenta de servicio) y guardar el `usuarioId` como atributo; hoy el alta y el `usuarioId` son manuales. |
| PROD con Keycloak | Base propia, HTTPS, sin el cliente `eduspace-e2e`, `sslRequired` externo; hoy solo hay perfil de DEV. |
| e2e con Keycloak | `tools/e2e/lib.py` crea usuarios por `auth-ms`; para correrlo con Keycloak habría que crearlos en el realm. |
| Comprobación en navegador | Verificada con Chrome headless (Playwright) para todos los roles; falta una prueba manual de rendir un examen con varios intentos. |
| Web: huecos conocidos | Revisión de preguntas abiertas (sin endpoint de respuestas), comprobante PDF, `descuentos/asignar` devolvió 409 en una prueba (regla sin investigar). |
