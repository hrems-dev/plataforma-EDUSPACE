# EduSpace — Plataforma web (Angular 22)

Plataforma web para todos los roles: **ADMIN, SECRETARIA, DOCENTE, APODERADO, ALUMNO y CAJERO**. El menú y las rutas se filtran por rol (`layout/menu.ts`, `roleGuard`). Habla solo con el Gateway (`:18080`); en desarrollo `ng serve` lo alcanza por `proxy.conf.json`
(`/api` → `http://localhost:18080`), así que no hay CORS que configurar.

## Cómo correrlo
```bash
cd frontend
npm install
npm start            # http://localhost:4200  (necesita el backend arriba: tools/e2e/levantar.sh)
npm test             # Vitest: sesión, interceptor y guards
npm run build        # producción en dist/
```
Usuarios de DEV: los de `tools/e2e/README.md` (`admin`, `secretaria`). Un usuario creado desde el panel recibe una clave temporal y debe cambiarla al entrar.

## Pantallas
| Ruta | Quién | Qué hace |
|---|---|---|
| `/login`, `/cambiar-clave` | todos | Login contra `auth-ms`; la clave temporal obliga a cambiarla |
| `/inicio` | ADMIN, SECRETARIA | Indicadores: año activo, secciones, docentes, usuarios, morosos, alertas |
| `/usuarios` | ADMIN | Crear/editar, roles, activar/desactivar, desbloquear, restablecer clave (se muestra una sola vez) |
| `/alumnos`, `/alumnos/:id` | ADMIN, SECRETARIA | Alta de alumno con apoderado (nuevo o ya registrado), expediente: datos, **documentos con checklist** (subir, verificar, descargar), apoderados, ficha de salud, estado e historial |
| `/matriculas` | ADMIN, SECRETARIA | Pre-matrícula, detalle con requisitos e historial, anular, trasladar, retirar y **ratificación masiva** |
| `/matriculas/campanias`, `/matriculas/cupos` | ADMIN, SECRETARIA | Campañas (crear/abrir/cerrar solo ADMIN) y vacantes por sección |
| `/planificacion/anios` | ADMIN, SECRETARIA | Años académicos (activar/cerrar) y sus periodos |
| `/planificacion/secciones`, `cursos`, `docentes`, `aulas` | ADMIN, SECRETARIA | CRUD de planificación |
| `/pagos/conceptos`, `tarifas`, `descuentos` | ADMIN | Catálogo de cobranza (una tarifa existente solo se activa/desactiva) |
| `/pagos/morosos` | ADMIN, SECRETARIA | Morosidad y alertas de cobranza |

### Pantallas por rol (además de las anteriores)
| Rol | Rutas | Qué hace |
|---|---|---|
| ADMIN / SECRETARIA | `/planificacion/*` (malla, asignaciones, bloques, horarios, feriados) | Horario en grilla con validación y publicación (avisa si queda incompleto). La secretaria solo edita horarios, asignaciones y docentes |
| ADMIN / SECRETARIA | espacios de trabajo | El menú del personal se organiza en 8 espacios con pestañas (`layout/hubs.ts`); ver `docs/09-espacios-de-trabajo.md`. Pantallas nuevas: `/planificacion/panel`, `/planificacion/cursos-docentes`, `/planificacion/aulas-disponibles` |
| ADMIN / SECRETARIA | `/planificacion/horarios-auto` | **Armado automático** en 3 pasos: (1) cursos y horas por grado en lote (solo ADMIN), (2) qué secciones están listas, (3) simular o generar horarios sin cruces de docente, aula ni sección; opcionalmente asigna docentes por especialidad. Algoritmo en `planificacion-ms` (`horario/PlanificadorHorario`) |
| ADMIN / SECRETARIA | `/asistencia/*`, `/calificaciones/*` | Justificaciones, reporte de asistencia, cierre de periodo (SECRETARIA/ADMIN; reabrir solo ADMIN), boletines |
| ADMIN | `/pagos/*`, `/notificaciones/envio` | Cuentas, cobranza; avisos: enviar a docentes, familias de una sección, alumnos concretos o números de usuario; CRUD de plantillas (las del sistema no se borran) y de los avisos enviados |
| DOCENTE | `/docente/*` | Inicio, horario, hoja de asistencia, tareas y entregas, notas, banco de preguntas y exámenes |
| APODERADO / ALUMNO | `/familia/*` | Resumen, asistencia (justificar), notas, horario, tareas con entrega a MinIO, exámenes con reloj; **Pagos solo apoderado** |
| CAJERO | `/caja` | Apertura, búsqueda de alumno, cobro de cuotas, arqueo y cierre |

### Sitio público (sin sesión) y páginas comunes
- **Sitio del colegio** (contenido de ejemplo en `src/app/features/publico/datos.ts`): `/`, `/nosotros`, `/niveles`, `/admision` (pasos, requisitos, calculadora de pensión, pre-inscripción de demostración), `/noticias` y `/contacto`. El botón «Ingresar al portal» lleva al login y luego a `/portal`, que redirige a la pantalla de cada rol.
- **Comunes a todos los roles:** `/calendario` (feriados, periodos y campañas), `/ayuda` (guías por rol) y `/mi-perfil`.
- **Nuevas por rol:** `/finanzas` (ADMIN/SECRETARIA), `/docente/alumnos`, `/familia/boletines`.

### Atajos y ayudas de uso
- **Inicio (ADMIN/SECRETARIA):** "Necesita tu atención" (morosos, cupos llenos, campaña cerrada, pre-matrículas, justificaciones, secciones sin tutor, cada una con botón para resolverla), indicadores, **estadísticas de matrícula** (ocupación total y por nivel), **alumnos en pre-matrícula** con los días que les quedan, **aulas** en lista vertical (al elegir una: aula, tutor —cambiable por el ADMIN—, matriculados, pre-matrícula, vacantes, asistencia promedio, alumnos con faltas, cursos y docentes), **cursos y docentes** (vista por curso o por docente, alta de curso con su malla y asignación de docentes), lista de **puesta en marcha** del año y accesos rápidos. La tarjeta de atención empieza cerrada: la campana muestra cuántos pendientes hay.
- **Ctrl + K** (o "Buscar…" del menú): salta a cualquier pantalla permitida para el rol o abre el expediente de un alumno por nombre/documento.
- **Cupos:** el ADMIN cambia la capacidad en la propia fila (valida contra los matriculados y la capacidad del aula).
- Enlaces con parámetros: `/matriculas?nueva=1`, `/matriculas?estado=PRE_MATRICULA`, `/alumnos?nuevo=1`.

## Estructura (como `frontPagatu`)
`core/` (models, services, guards, interceptors) · `layout/` (shell con menú por rol) · `shared/` (modal, toast, confirm, page-header) · `features/`.

## Dos modos de inicio de sesión (`public/config.json`, se lee al arrancar: no hace falta recompilar)
- `eduspace` (por defecto): formulario propio contra `auth-ms`; refresh con rotación.
- `keycloak`: redirige al login de Keycloak (código de autorización + PKCE, `keycloak-js`); el panel no arranca sin sesión. La pantalla
  "Usuarios y roles" se reemplaza por un enlace a la consola de Keycloak y "Cambiar clave" abre la cuenta del usuario. Ver `infra/keycloak/README.md`.
```bash
npm run auth:keycloak   # copia public/config.keycloak.json → config.json
npm run auth:eduspace   # vuelve al login propio
```

## Seguridad del cliente
- Access y refresh token en `sessionStorage` (se pierden al cerrar la pestaña). El interceptor agrega el `Bearer`, y ante un 401 renueva **una sola vez**
  (el refresh token rota en cada uso; reutilizar uno viejo revoca todas las sesiones) y repite la petición.
- Los guards solo evitan pantallas inútiles: **el backend valida el rol en cada llamada**.
- Sin `innerHTML` ni `bypassSecurityTrust*`: Angular escapa todo lo que se muestra.

## Reglas del backend que la interfaz respeta
- Una matrícula exige **campaña abierta, cupo y expediente completo**: DNI, partida de nacimiento y ficha SIAGIE **subidos y verificados**.
- La pre-matrícula pasa a matrícula **al registrarse el pago** (evento de `pago-ms`); solo una matrícula confirmada se puede trasladar (mismo grado y año) o retirar.
- El monto de una tarifa existente no se cambia: se desactiva y se crea otra.

## Pendiente
- Revisión de preguntas abiertas de un examen: el backend no expone un endpoint para listar las respuestas, la interfaz no puede mostrarlas.
- Descarga del comprobante en PDF: no hay endpoint en la API.
- Apps móviles y de escritorio; ficha de salud con control de acceso por tutor y docentes de la sección.

## Verificación en navegador
Se hizo con Playwright + Chrome headless contra el entorno local (Keycloak 18090, Gateway 18080, `ng serve` 4200): recorridos de admin, docente, apoderado, alumno, cajero y secretaria sin errores de consola ni respuestas 4xx/5xx. Datos de prueba: `python3 tools/demo/semilla.py todo`.
