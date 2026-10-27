# EduSpace — Informe de estado

Fecha: 2026-10-16 · Curso DIST 2026-2, UPeU · Arquitectura base "pagatu" · Detalle técnico: `docs/05-integracion-y-correcciones.md`

## 1. Resumen

Los **10 servicios con base de datos (incluido `auth-ms`) están construidos, probados y funcionando juntos** (Config Server, Eureka, Gateway, Kafka,
MinIO y Mailpit reales), más la infraestructura. Falta únicamente el front. Esta etapa:

1. corrigió los servicios de Witmer frente al plan (eventos, comprobantes, cuotas, identidad, PDF, pendientes persistentes…);
2. construyó `matricula-ms`, `asistencia-ms` y `tareas-ms` completos según el informe de Andre (el repositorio no traía su avance);
3. verificó todo con **255 pruebas automáticas** y **6 escenarios de punta a punta** por consola (`tools/e2e/`).

| Servicio | Responsable | Estado | Pruebas |
|---|---|---|---|
| auth-ms | infra | completo (construido en esta etapa) | 15 |
| planificacion-ms · alumnos-ms · notificaciones-ms | Hermes | completos | 36 · 35 · 43 |
| matricula-ms · asistencia-ms · tareas-ms | Andre | completos (construidos en esta etapa) | 24 · 21 · 21 |
| calificaciones-ms · pago-ms · examenes-ms | Witmer | corregidos | 16 · 23 · 21 |

## 2. Resultado de la corrida de punta a punta

Con tokens RS256 reales y todos los servicios arriba, recorre: planificación y alumnos → campaña, matrícula y cupos (el tercer
alumno es rechazado por falta de cupo) → cobro en caja con comprobante único → `pago.confirmado` confirma la matrícula → se
generan las 10 pensiones → las réplicas de matrícula aparecen en asistencia, tareas, exámenes y calificaciones → asistencia
(una sola notificación de falta, justificación aprobada) → tarea con archivo en MinIO y nota → examen rendido → criterios,
promedio ponderado, cierre del periodo, boletín PDF descargado desde MinIO, libretas por dueño → Gateway → traslado y retiro
(las cuotas se anulan) → correos en Mailpit.

La corrida real encontró fallos que las pruebas con servicios simulados no ven (por eso se mantienen los dos niveles):
`LocalDate` enviado por Feign como `15/10/26`, IP de la máquina registrada en Eureka, 404 de `alumnos-ms` convertido en 500,
credenciales de MinIO distintas entre servicios, bucket equivocado en `tareas-ms`.

## 3. Lo que falta

### 3.1 Infraestructura
- `auth-ms` ya existe. En PROD requiere sus claves RSA (`JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH`) y cambiar las claves de las semillas.
- El Gateway enruta `/api/v1/auth/**` pero aún no valida el JWT (cada servicio sí lo valida).
- Front (web/escritorio).
- PROD (`compose.yml`, `application-prod.yml`) escrito pero no levantado en `eduspace-prod-net`; sin pruebas de carga.

### 3.2 Funcionalidad con límites conocidos
Ver `PENDIENTES.md` (FCM/SMS, apoderado sin cuenta, consumidor de `tarea.recordatorio`, tutor en ficha de salud, etc.).

### 3.3 Acuerdos que conviene confirmar con el equipo
1. **Tarifa de pensión:** `monto` es el **total** del año y se reparte en `num_cuotas` (así lo describe el informe de Witmer).
   Si el colegio carga el monto mensual, hay que dividirlo antes o cambiar la regla.
2. `horario.publicado` envía `versionHorario` (el `version` del sobre es la versión del contrato).
3. Paquetes distintos (`pe.edu.eduspace` de Hermes y `pe.edu.upeu.eduspace` de Witmer/Andre).

## 4. Decisiones que se apartan del informe

1. `versionHorario` en lugar de `version` en `horario.publicado`.
2. Notificación PENDIENTE sin destinatario conocido = fila marcadora `usuario_id = 0`, resuelta por `ReintentoScheduler`.
3. `V3__plantillas_horario_y_correo.sql` aditiva en notificaciones.
4. Código de alumno `ALaaaa-NNNNN` bajo candado de transacción.
5. `V2` aditivas en matricula (secuencia de código), asistencia (`confirmado`, `falta_notificada`), tareas (`recordatorio_enviado`),
   pago (`alertas_admin`), calificaciones (`notas_pendientes`) y examenes (índice único del intento en curso); en examenes se
   amplió `preguntas.tipo` a `VARCHAR(16)` en `V1` (`OPCION_MULTIPLE` no cabía en 12).
6. Sin MapStruct en los servicios de Andre (mapeo manual con *records*): menos magia, mismo resultado.

## 5. Estado de las carpetas

- `eduspace-completo/` — el proyecto (esta carpeta).
- `eduspace-s9/` — actividad autónoma S9; se conserva intacta.
- `eduspace/` — trabajo anterior; no se usó.
