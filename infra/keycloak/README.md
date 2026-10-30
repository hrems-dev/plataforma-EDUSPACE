# Keycloak (solo DEV) — emisor de tokens de DEV

EduSpace tiene **dos emisores de token con el mismo contrato** (JWT RS256, `usuarioId` numérico, `realm_access.roles`), igual que Pagatu
(Keycloak en DEV, `auth-ms` en PROD):

| | `auth-ms` (PROD y escenarios e2e) | Keycloak (por defecto en DEV, como Pagatu) |
|---|---|---|
| Login | `POST /api/v1/auth/login` (usuario y clave) | Código de autorización + **PKCE** (la app nunca ve la clave) |
| Quién firma | `auth-ms` | Keycloak (realm `eduspace`) |
| Bloqueo por intentos | 5 intentos → 15 min | 5 intentos → espera creciente (máx. 15 min) |
| Usuarios y roles | Pantalla "Usuarios y roles" del panel | Consola de Keycloak |
| Servicios | `JWK_SET_URI` = JWKS de auth-ms | `JWK_SET_URI` = JWKS de Keycloak |

Cambiar de emisor **no toca el código de los servicios**: solo la variable `JWK_SET_URI` (Gateway y los 9 servicios la leen) y el `config.json` del front.

## Tres formas de iniciar sesión (`frontend/public/config.json` → `authMode`)

| `authMode` | Qué hace | Dónde sirve |
|---|---|---|
| `keycloak-form` | El **formulario de EduSpace** envía usuario y clave a Keycloak (credenciales directas, cliente `eduspace-web-form`); Keycloak valida y emite el token. En DEV el navegador llega a Keycloak por el proxy `/kc` del servidor de la web | **Cualquier equipo de la red por http** (no usa Web Crypto). Recomendado en DEV |
| `keycloak` | Redirección a la pantalla de Keycloak (código + PKCE) | Solo `http://localhost:4200` o `https` (necesita Web Crypto) |
| `eduspace` | Login propio de auth-ms | Cualquier equipo; cuentas y claves se gestionan en la web |

Cambiar todo el sistema de una vez (config, Gateway y servicios): `tools/e2e/modo.sh keycloak-form|keycloak|propio`.
Las cuentas de la demo (`jorge.paredes`, `lucia.mendoza`, `carmen.rios`) se crean en Keycloak con `python3 tools/demo/sincronizar_keycloak.py` (clave `Expo12345`, mismo `usuarioId` que en EduSpace).
En producción conviene `keycloak` con HTTPS (PKCE); las credenciales directas son una comodidad de DEV.

## Levantarlo
```bash
cd infra/keycloak && ./levantar-dev.sh          # http://localhost:18090 · consola: admin / admin · --detener para apagarlo
```
Usa Podman o Docker (`CONTENEDORES=docker`). El realm `eduspace` se importa desde `eduspace-realm.json`:
- Roles: `ADMIN`, `SECRETARIA`, `DOCENTE`, `ALUMNO`, `APODERADO`, `CAJERO`.
- Clientes: `eduspace-web` (público, código + PKCE S256, sin *password grant*) y `eduspace-e2e` (solo DEV, permite pedir token con usuario y clave desde scripts).
- Usuarios de DEV (los mismos de `tools/e2e/README.md`): `admin`, `secretaria`, `cajero`, y `docente`, `apoderado`, `alumno` (clave `Clave12345`).
- El atributo `usuarioId` de cada usuario se emite como claim numérico: **debe coincidir con el `id` en `auth-ms`**, porque todos los servicios guardan ese número
  (docente, apoderado, responsable de pago…). El `sub` de Keycloak es un UUID y los servicios no lo usan.

## Usarlo de punta a punta
```bash
# 1) backend confiando en Keycloak (es el valor por defecto de DEV en config-repo; levantar.sh también levanta Keycloak)
tools/e2e/levantar.sh                    # --auth-propio para que confíen en auth-ms (escenarios e2e)
# 2) front en modo Keycloak
cd frontend && npm run auth:keycloak && npm start      # npm run auth:eduspace para volver al login propio
```
En este modo `auth-ms` sigue corriendo pero **sus tokens ya no se aceptan** (otro emisor) y la pantalla de usuarios propia se reemplaza por un enlace a la consola de Keycloak.

## Limitaciones conocidas
- Las altas de usuario en Keycloak no se reflejan en `auth-ms` ni al revés; el `usuarioId` hay que asignarlo a mano (atributo del usuario).
  Para PROD con Keycloak haría falta que `auth-ms` administre los usuarios por la Admin API de Keycloak (ver `PENDIENTES.md`).
- `tools/e2e` crea sus usuarios de prueba por la API de `auth-ms`, así que los 6 escenarios se corren con `levantar.sh --auth-propio`.
- El realm usa `sslRequired: none` y `start-dev` (H2 en memoria): **nunca en PROD**. En PROD: HTTPS, base propia, sin `eduspace-e2e`.
