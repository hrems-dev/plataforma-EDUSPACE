# EduSpace — documentación del sistema

**Sistema de gestión escolar distribuido** para un colegio de Inicial, Primaria y Secundaria (Colegio Horizonte del Valle).
Curso *Desarrollo de Aplicaciones Distribuidas* (DIST 2026-2, UPeU) · arquitectura de referencia **Pagatu** · documentación organizada
según la plantilla mínima de la guía del Proyecto Sello (`class/pagatu/docs/proyecto-sello/u3-producto.md`, Tabla 4).

## Problema y solución

| | |
|---|---|
| **Problema** | Un colegio lleva en hojas sueltas la matrícula, la asistencia, las notas, los cobros y los horarios; la información se duplica, se desfasa y las familias no se enteran a tiempo. |
| **Solución** | Un sistema de **10 microservicios con base de datos propia** que se hablan por API (síncrona) y por eventos (Kafka), detrás de un Gateway único, con un portal web por rol (personal, docente, familia, caja) y un sitio público. |
| **Valor** | Una sola fuente de verdad por dato; el alumno matriculado aparece solo en asistencia, tareas, exámenes, notas y cobranza; las familias reciben avisos; el horario se arma sin cruces en segundos. |

## Cifras clave (medidas el 2026-10-29)

| Indicador | Valor |
|---|---|
| Microservicios con base de datos | **10** (+ Config Server, Eureka, Gateway) |
| Endpoints REST | **316** en los 10 servicios |
| Tablas de negocio | **~90** en 10 bases PostgreSQL (101 contando las técnicas de Flyway) |
| Eventos Kafka | **25 tópicos** + 18 colas de errores (DLT) |
| Código Java (main) | **~33 800 líneas** en 784 archivos · tests ~5 800 líneas |
| Pruebas automáticas | **~280** en el backend (`@Test`) · **62** en el frontend · **6** escenarios de punta a punta |
| Frontend | Angular 22, 82 componentes, 66 rutas, **105 kB** de carga inicial (comprimido) |
| Latencia típica por el Gateway | **10–70 ms** mediana en consultas de lectura |
| Armado automático de horarios | **12 secciones / 288 clases en 0,3–0,6 s**, 0 cruces |

Detalle y método en [Pruebas y métricas](pruebas-metricas.md).

## Mapa de esta documentación

| Página | Qué encuentras (plantilla Pagatu) |
|---|---|
| [Instalación y ejecución](instalacion.md) | Requisitos, comandos, puertos, usuarios de prueba |
| [Uso del sistema](uso.md) | Flujo principal, casos de uso por rol, cómo probarlo |
| [Arquitectura](arquitectura.md) | Diagramas, componentes, flujos y decisiones técnicas |
| [Módulos y funcionalidades](modulos.md) | Qué hace cada microservicio y cada espacio del portal |
| [Alumnos y entrada al sistema](alumnos-y-acceso.md) | Expediente del alumno, cómo se ingresa, cuentas (usuario nombre.apellido, clave = DNI), restauración |
| [Matrícula en línea](matricula-en-linea.md) | La familia matricula, paga y recibe constancia, cuenta y horario por correo |
| [Horarios](horarios.md) | Modelo, reglas, armado automático, aulas disponibles y API |
| [Datos y consistencia](datos.md) | Modelo por servicio, eventos, outbox, idempotencia |
| [Seguridad](seguridad.md) | Autenticación, autorización, roles, endpoints internos, hallazgos |
| [Observabilidad](observabilidad.md) | Salud, métricas, logs, trazas, paneles |
| [Frontend](frontend.md) | Estructura Angular, roles, espacios de trabajo |
| [Pruebas y métricas](pruebas-metricas.md) | Pruebas, evidencias y cifras medidas |
| [Operación y problemas frecuentes](operacion.md) | Ambientes, reinicios, errores conocidos |
| [Decisiones de diseño](decisiones.md) | Por qué se hizo así |
| [Equipo y aporte individual](equipo.md) | Quién hizo qué (con enlace a un documento por persona) |
| [Repositorio y estándares](repositorio.md) | Estructura, convenciones, commits |
| [Limitaciones y mejoras](limitaciones.md) | Qué falta y qué sigue, priorizado |
| [Glosario](glosario.md) | Términos del dominio y de la arquitectura |

## Resumen del proyecto (informe)

EduSpace cubre el ciclo escolar completo: **planificación** del año (secciones, aulas, cursos, docentes, horarios), **expediente** del alumno,
**matrícula** con control de cupos y pago, **asistencia** con justificaciones, **tareas** y **exámenes** en línea, **calificaciones** con boletín PDF,
**cobranza** (conceptos, tarifas, cuotas, caja, mora) y **notificaciones** a las familias. Cada servicio es dueño de sus datos; lo que otro necesita
llega por evento (réplicas locales como `matriculas_local`) o por consulta a endpoints internos protegidos con un token de servicio.
La entrada es un Gateway que valida el JWT, aplica CORS y límites de peticiones; la identidad en DEV la emite Keycloak (PKCE) y en PROD `auth-ms`.
Todo se probó con pruebas con PostgreSQL real, escenarios de punta a punta por consola y verificación en navegador por rol.
