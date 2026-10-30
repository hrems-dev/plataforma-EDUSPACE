# Seguridad

Alineada con la sesión S7 de Pagatu (seguridad distribuida y control de acceso).

## Capas
| Capa | Mecanismo |
|---|---|
| Identidad | **Keycloak 26** (realm `eduspace`, cliente `eduspace-web` público con **código + PKCE S256**, sin *password grant*) en DEV; `auth-ms` (JWT **RS256** + JWKS, refresh con rotación y detección de reutilización, bloqueo tras 5 intentos → 423) en PROD |
| Borde | Gateway: valida JWT (JWKS), CORS con origen configurado, límite multipart, límite de peticiones por IP (429), solo `/actuator/health` e `info` sin token |
| Servicio | Cada servicio **vuelve a validar** el JWT (`jwk-set-uri`; en DEV apunta a Keycloak, en PROD a `auth-ms`) y aplica roles |
| Servicio a servicio | `/internal/**` exige `X-Service-Token` (comparación en tiempo constante); los clientes Feign lo envían solo hacia `/internal/`; servicios sin endpoints internos lo deniegan; en PROD el token es obligatorio (no arranca sin él) |
| Datos | Ficha de salud cerrada por defecto; apoderado solo ve a sus hijos; alumno solo lo propio; banco de preguntas solo editable por su docente |

## Endpoints públicos (sin sesión)
Solo `GET/POST /api/v1/publico/**` (matrícula en línea). Controles: límite propio por IP en el Gateway, validación de entrada, campo trampa, token por trámite
(hash SHA-256), reservas que vencen en 1 día y mensajes que no revelan datos de terceros. Detalle: [Matrícula en línea](matricula-en-linea.md).
Los datos de tarjeta de la pasarela simulada no se guardan ni se registran.

## Cuentas de personas
> **Modos de login.** `eduspace` (auth-ms), `keycloak` (PKCE, solo localhost/https) y `keycloak-form` (formulario de EduSpace validado por Keycloak, sirve por http en la red). Ver `infra/keycloak/README.md` y `tools/e2e/modo.sh`.

Usuario `nombre.apellido`, clave inicial = documento, cambio obligatorio al primer ingreso, restauración por el ADMIN (no aplica a cuentas ADMIN). Detalle: [Alumnos y entrada al sistema](alumnos-y-acceso.md).

## Identidad: el claim `usuarioId`
El token trae `usuarioId` (obligatorio; sin él 403). **Ningún servicio asume `usuarioId == alumnoId/docenteId`**: se traduce con
`/internal/docentes/usuario/{id}`, `/internal/alumnos/usuario/{id}` y `/internal/apoderados/usuario/{id}/alumnos`. Un 404 significa «no es docente/alumno» y se trata como 403.

## Roles
`ADMIN`, `SECRETARIA`, `DOCENTE`, `APODERADO`, `ALUMNO`, `CAJERO` (en `realm_access.roles`).

| Área | ADMIN | SECRETARIA | DOCENTE | APODERADO/ALUMNO | CAJERO |
|---|---|---|---|---|---|
| Estructura (secciones, aulas, cursos, malla, años) | escribe | lee | — | — | — |
| Horarios, asignaciones, docentes | escribe | escribe | lee lo propio | lee lo propio | — |
| Alumnos, matrículas, cupos | todo | todo | lista de sus secciones | lo propio / hijos | — |
| Asistencia | todo | todo | registra en sus secciones | lee lo propio; el apoderado justifica | — |
| Tareas, exámenes | — | — | crea y califica | entrega / rinde | — |
| Materiales del curso | borra | — | sube y borra los de sus cursos | ve los de su sección | — |
| **Aprobar o rechazar matrículas** | **solo ADMIN** | 403 | — | — | — |
| Notas, boletines | todo | cierra y genera | registra | lee lo propio | — |
| Conceptos, tarifas, descuentos | escribe | — | — | — | — |
| Cobranza, morosos | todo | todo | — | el apoderado paga a sus hijos | cobra en caja |
| Plantillas y avisos | todo | — | — | bandeja propia | — |
| Usuarios | todo | — | — | — | — |

(Matriz resumida a nivel de pantalla y de servicio; la regla exacta está en el `SecurityConfig`/`@PreAuthorize` de cada servicio y en `layout/menu.ts` / `app.routes.ts` del frontend.)

## Frontend
- Login por PKCE; `check-sso` silencioso (sin bloquear la página pública); el token solo se adjunta a la **API propia** (`esApiPropia`), nunca a otros orígenes.
- Cada petición lleva `X-Trace-ID` (UUID) para seguirla entre servicios.
- Si el servidor rechaza un token de Keycloak (servicios configurados con `auth-ms`), se muestra un aviso claro en lugar de una pantalla vacía.
- `roleGuard`/`modoGuard` impiden abrir rutas no permitidas; el menú solo muestra lo permitido (probado: `menu.spec.ts`).
- `referrer` restringido y título neutro en `index.html`.

## Hallazgos y estado
| Hallazgo | Estado |
|---|---|
| Frontend en Keycloak vs servicios en auth-ms → 401 en todo | **Corregido**: Keycloak por defecto en DEV (`jwk-set-uri` con `JWK_SET_URI`), aviso en el interceptor, `levantar.sh --auth-propio` |
| Cualquier APODERADO podía pagar cuotas ajenas (`POST /pagos/online`) | **Corregido** (403) |
| `/internal/**` sin autenticación | **Corregido** (token de servicio) |
| Gateway sin JWT/CORS/límites | **Corregido** |
| Actuator y Swagger en `permitAll` dentro de cada servicio | **Abierto**: en PROD solo se limita la exposición; cerrar a la red de observabilidad |
| Cuentas semilla (`admin`, `secretaria`, `cajero`) sin cambio de clave obligatorio | **Abierto** (PROD) |
| Sin 2FA, sin `aud`, sin revocación del access token (30 min) | **Abierto** |
| Rate limit por réplica y en memoria | **Abierto** (Redis si hay varias réplicas) |
| Cifrado en reposo de datos de salud, derechos ARCO (Ley 29733) | **Abierto**: decisión legal/producto |
| Cabeceras de seguridad/CSP en el servidor del frontend | **Abierto** |
