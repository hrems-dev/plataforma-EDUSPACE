# infra

Infraestructura compartida de EduSpace (sin lógica de negocio), igual que `pagatu/infra`.

| Carpeta | Qué es | Puerto |
|---|---|---|
| `eduspace-config/` | Config Server; sirve `config-repo/` (`<servicio>-dev.yml`, `-prod.yml`) | 18888 |
| `eduspace-eureka/` | Registro y descubrimiento | 18761 |
| `eduspace-gateway/` | Punto único de acceso, rutas `lb://` a los servicios | 18080 |

Orden en DEV: config → eureka → gateway. Cada uno: `cd infra/<carpeta> && ./mvnw spring-boot:run`
(el Config Server lee `./config-repo`).

## Seguridad del Gateway
- Valida el JWT RS256 (JWKS de auth-ms) antes de enrutar; solo `POST /api/v1/auth/{login,refresh,logout}` y `/actuator/health|info` pasan sin token.
- Límite de peticiones por IP (`eduspace.gateway.rate-limit.*`): API 300/min (ráfaga 50) y login/refresh 10/min (ráfaga 5); 429 con `Retry-After`.
- `/internal/**` de los servicios no se enruta y además exige `X-Service-Token` (`SERVICE_TOKEN`; obligatorio en PROD).
- PROD: `infra/eduspace-gateway/compose.yml` (requiere `CORS_ALLOWED_ORIGINS`), perfil `prod` en `config-repo/eduspace-gateway-prod.yml`.

