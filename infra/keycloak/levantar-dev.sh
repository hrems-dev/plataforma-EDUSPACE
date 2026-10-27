#!/bin/bash
# Levanta Keycloak (solo DEV) con Podman o Docker y el realm "eduspace" importado. Uso: ./levantar-dev.sh   |   ./levantar-dev.sh --detener
# El realm usa sslRequired=none: con Podman rootless las peticiones desde el host llegan como "externas" y Keycloak exigiría HTTPS.
AQUI="$(cd "$(dirname "$0")" && pwd)"; CT=${CONTENEDORES:-podman}; NOMBRE=eduspace-keycloak; PUERTO=${KC_PUERTO:-18090}
$CT rm -f $NOMBRE >/dev/null 2>&1
[ "$1" = "--detener" ] && { echo "Keycloak detenido."; exit 0; }
$CT run -d --name $NOMBRE -p $PUERTO:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=${KC_ADMIN:-admin} -e KC_BOOTSTRAP_ADMIN_PASSWORD=${KC_ADMIN_PASSWORD:-admin} \
  -e KC_HEALTH_ENABLED=true -e JAVA_OPTS_APPEND="-Xms256m -Xmx640m" \
  -v "$AQUI/eduspace-realm.json:/opt/keycloak/data/import/eduspace-realm.json:ro,z" \
  quay.io/keycloak/keycloak:26.1.0 start-dev --import-realm >/dev/null || exit 1
echo -n "Esperando a Keycloak"
until $CT exec $NOMBRE /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master \
      --user ${KC_ADMIN:-admin} --password ${KC_ADMIN_PASSWORD:-admin} >/dev/null 2>&1; do echo -n "."; sleep 3; done; echo
# La consola de administración (realm master) también necesita HTTP en DEV.
$CT exec $NOMBRE /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=NONE >/dev/null
echo "Keycloak listo: http://localhost:$PUERTO  (consola: admin / ${KC_ADMIN_PASSWORD:-admin})  ·  realm: http://localhost:$PUERTO/realms/eduspace"
