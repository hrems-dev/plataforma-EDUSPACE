# Decisiones de diseño

| # | Decisión | Motivo | Consecuencia |
|---|---|---|---|
| 1 | `academico-ms` se dividió en planificacion y alumnos | Un módulo enorme era un cuello de botella y mezclaba responsabilidades | 10 servicios con responsabilidades claras |
| 2 | Un solo colegio (sin multi-tenant) | Alcance del curso | Modelo más simple |
| 3 | Base de datos por servicio + réplicas locales (`matriculas_local`) | Autonomía y rendimiento; nadie lee bases ajenas | Consistencia eventual, resuelta con eventos |
| 4 | **Outbox** en los productores | No perder ni inventar eventos | Un relé periódico y limpieza |
| 5 | Sobre de evento **plano** y texto JSON | Los consumidores no deben conocer clases del productor; `JsonSerializer` con cabeceras de tipo falla con Kafka real | `EventoSobre` en cada servicio; Jackson 3 |
| 6 | `versionHorario` en `horario.publicado` | `version` del sobre es la del contrato | Los consumidores lo leen con ese nombre |
| 7 | Keycloak en DEV, `auth-ms` en PROD | Igual que Pagatu; `auth-ms` es temporal | Los servicios cambian de emisor solo con `JWK_SET_URI` |
| 8 | `/internal/**` con token de servicio y fuera del Gateway | Evitar llamadas internas desde el exterior | Feign envía `X-Service-Token` |
| 9 | Ningún servicio asume `usuarioId == id de persona` | La identidad del token no es la del dominio | Endpoints internos de traducción |
| 10 | **El monto de la tarifa de pensión es el total del año** y se reparte en `num_cuotas` | Lo describe el informe de pago | Confirmar con el colegio si prefieren cargar el mensual |
| 11 | Cupo con `UPDATE … WHERE ocupados < capacidad` | Evitar sobrematrícula con concurrencia sin bloqueos largos | Una sola sentencia atómica |
| 12 | Examen con **tiempo del servidor** | No fiarse del reloj del cliente | `vence_en` y job de cierre |
| 13 | Notas de tareas/exámenes sin criterio quedan **pendientes** persistentes | No perder notas por eventos tempranos | Tabla `notas_pendientes` |
| 14 | Inicial se nombra por **edad** (3, 4, 5 años) | Así existe en el Perú (ciclo II) | Migración V3 de planificación |
| 15 | Horario automático con **restricciones duras y semilla fija** | Resultados válidos y repetibles; la simulación usa el mismo código | `PlanificadorHorario` + `setRollbackOnly` |
| 16 | Menú del personal en **espacios con pestañas** sobre las mismas rutas | Reducir saltos entre pantallas sin romper enlaces | `layout/hubs.ts` |
| 17 | Funciones duplicadas a propósito entre paneles y pantallas CRUD | Hacer cada tarea en un solo lugar | Componentes compartidos (`asignar-docente`, `seccion-form`) |
| 18 | Un paquete base por integrante (`pe.edu.eduspace` vs `pe.edu.upeu.eduspace`) | Herencia del trabajo en paralelo | Unificar queda pendiente |
| 19 | Push como `LogPushSender`, pasarela de pago simulada, comprobantes internos | Fuera del MVP (FCM, pasarela, SUNAT = fase 2) | Documentado en limitaciones |
| 20 | Zona horaria `America/Lima` y fechas en ISO | Evitar desfases en vencimientos y asistencia | Configurado en todos |
