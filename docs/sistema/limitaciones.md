# Limitaciones y mejoras

Priorización: **P1** antes de PROD · **P2** próxima iteración · **P3** fase 2.

## Limitaciones actuales
| # | Limitación | Prio | Detalle |
|---|---|---|---|
| 1 | PROD no levantado | P1 | `compose.yml`/`application-prod.yml` escritos; falta `eduspace-prod-net`, claves RSA reales, token de servicio como secreto, HTTPS |
| 2 | Actuator/Swagger en `permitAll` en cada servicio | P1 | Cerrar a la red de observabilidad |
| 3 | Cuentas semilla sin cambio de clave obligatorio; claves de compose de respaldo | P1 | Quitar de PROD |
| 4 | Cabeceras de seguridad/CSP en el servidor del frontend | P1 | Servirlo con nginx u otro |
| 5 | Pruebas de integración sin Docker | P1 | Configurar el socket de Podman o CI con Docker |
| 6 | Sin pruebas de carga ni cobertura medida | P2 | JaCoCo y escenarios de concurrencia (matrículas simultáneas) |
| 7 | Cuentas de usuario no vinculadas a alumnos/apoderados/docentes en la demo | P2 | Los avisos no llegan sin `usuarioId` |
| 8 | Usuarios en dos lugares con Keycloak | P2 | `auth-ms` debería administrarlos por la Admin API |
| 9 | Horarios solo generados para Primaria | P2 | Faltan cursos/docentes de Inicial y Secundaria; luego publicar |
| 10 | Un paquete base distinto por integrante | P2 | Unificar a `pe.edu.eduspace` |
| 11 | Un panel de Grafana por servicio | P2 | Hoy solo el panel base |
| 12 | Circuit breaker propio en calificaciones y exámenes | P2 | Hoy solo fallback de Feign |
| 13 | Escala literal solo de Primaria | P2 | Definir escalas de Inicial y Secundaria |
| 14 | Consumidores sin uso: `tarea.entregada`, `alumno.*`, `anio.activado`, `periodo.cambiado` | P3 | Útiles para avisar al docente y auditar |
| 15 | Salud: «solo tutor y docentes de la sección» | P2 | alumnos-ms debe consultar a matrícula |
| 16 | Apoderado sin cuenta no recibe avisos | P3 | Cambiar `usuario_id` NOT NULL y definir canal/consentimiento |
| 17 | Push, SMS y WhatsApp | P3 | `LogPushSender`; falta proveedor |
| 18 | Pasarela de pago real y comprobante SUNAT | P3 | Simulado (también en la matrícula en línea); comprobantes internos |
| 18a | La aprobación de matrícula no exige expediente completo y el rechazo no devuelve el dinero automáticamente (se devuelve desde Finanzas); para matrículas presenciales no se envía correo de acceso (las cuentas las crea secretaría) | P2 | Exigir requisitos según política; reembolso automático; correo de acceso también para matrículas presenciales |
| 18b | Matrícula en línea a mitad de año genera pensiones de meses pasados | P2 | Definir prorrateo; ver [Matrícula en línea](matricula-en-linea.md) |
| 18c | Correo real en PROD | P1 | Configurar `SMTP_*` en `notificaciones-ms`; en DEV el correo lo recibe Mailpit |
| 19 | Ratificación: «aprobó el año» | P3 | Hoy lo decide la secretaría; luego `resultados_anuales` |
| 20 | Recuperación, orden de mérito, panel del director, alta automática de cuentas | P3 | H14–H17 del plan |
| 21 | Revisión de preguntas abiertas en la web | P2 | Falta el endpoint de respuestas para la pantalla |
| 22 | Auditoría general | P3 | Mejor como `auditoria-ms` que consuma eventos |
| 23 | Cifrado en reposo de salud, derechos ARCO, 2FA, `aud`, revocación de token | P3 | Decisión legal/producto (Ley 29733) |
| 24 | Rate limit por réplica | P3 | Redis si hay varias réplicas |
| 25 | Móvil/escritorio nativos | P3 | El portal es responsive, no hay app nativa |

## Mejoras propuestas
1. Un `docker compose` único para todo (`infra/compose.yml`).
2. Prefijo `eduspace-` en los nombres de servicio (alinear con Pagatu; cambio grande).
3. Caché en `matriculas/cupos` si crecen las secciones.
4. Horarios: restricciones blandas (huecos del docente, horas pesadas seguidas) y suplencias.
5. Tablero del director con las métricas de esta documentación.

Lista viva: `PENDIENTES.md`.
