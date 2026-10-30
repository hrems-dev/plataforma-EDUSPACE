#!/usr/bin/env bash
# Cambia TODO el sistema de emisor de tokens de forma coherente (web + Gateway + servicios):
#   ./modo.sh keycloak-form → Keycloak valida y emite el token; el formulario de login es el de EduSpace (usuario y clave).
#                             Funciona por http desde otros equipos y móviles de la red (no usa Web Crypto). Recomendado en DEV.
#                             Usuarios de Keycloak: admin, secretaria, cajero, docente, alumno, apoderado + los de la demo
#                             (tools/demo/sincronizar_keycloak.py).
#   ./modo.sh keycloak      → Keycloak con SU pantalla de login: la web manda a Keycloak y vuelve (código + PKCE). Sirve también por http
#                             desde otros equipos (la web completa el Web Crypto que falta); abre el puerto 18090 de este equipo.
#   ./modo.sh propio        → login propio de auth-ms (usuario y clave); las cuentas se crean y restauran desde la web.
# Reinicia Config, Eureka, el Gateway y los 10 servicios (no toca los contenedores ni los datos).
set -e
AQUI="$(cd "$(dirname "$0")" && pwd)"; RAIZ="$(cd "$AQUI/../.." && pwd)"
MODO="$1"
case "$MODO" in keycloak|keycloak-form|propio) ;; *) echo "Uso: $0 keycloak-form|keycloak|propio"; exit 1;; esac
CFG="$RAIZ/frontend/public/config.json"
case "$MODO" in
  propio)        AUTH=eduspace;      KCURL=http://localhost:18090 ;;
  keycloak)      AUTH=keycloak;      KCURL=auto ;;   # auto = http(s)://<host de la web>:18090 (sirve desde otros equipos)
  keycloak-form) AUTH=keycloak-form; KCURL=/kc ;;   # /kc = proxy de DEV (proxy.conf.json) hacia Keycloak, mismo origen
esac
printf '{\n  "authMode": "%s",\n  "keycloak": { "url": "%s", "realm": "eduspace", "clientId": "eduspace-web", "clientIdFormulario": "eduspace-web-form" }\n}\n' "$AUTH" "$KCURL" > "$CFG"
echo "config.json → $AUTH"
[ "$MODO" != "propio" ] && python3 "$AQUI/keycloak_redirects.py" || true
for puerto in 18080 8099 8098 8097 8096 8095 8094 8093 8092 8091 8090 18761 18888; do
  for pid in $(ss -ltnp 2>/dev/null | grep ":$puerto " | grep -o 'pid=[0-9]*' | cut -d= -f2 | sort -u); do kill "$pid" 2>/dev/null || true; done
done
sleep 6
IP=$(ip -4 route get 1.1.1.1 2>/dev/null | grep -oP 'src \K[0-9.]+' | head -1)
ARG=""; [ "$MODO" = "propio" ] && ARG="--auth-propio"   # keycloak y keycloak-form: los servicios validan contra Keycloak (sin JWK_SET_URI)
EXPONER_HOST="${EXPONER_HOST:-$IP}" setsid nohup "$AQUI/levantar.sh" $ARG --sin-reset > "$AQUI/logs/levantar-modo.log" 2>&1 < /dev/null &
echo "Arrancando en modo $MODO (~2 min). Compruebe: tools/e2e/logs/levantar-modo.log"
