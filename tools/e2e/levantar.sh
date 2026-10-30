#!/bin/bash
# Levanta TODO en DEV con Podman/Docker: Kafka, MinIO, Mailpit, 9 PostgreSQL (puertos 15441-15449),
# Config Server, Eureka, auth-ms (:8090), los 9 servicios y el Gateway.
# Requisitos: podman (o docker) y jars compilados (./compilar.sh).
# Uso: ./levantar.sh        (recrea las bases desde cero)   |   ./levantar.sh --sin-reset  (solo arranca las aplicaciones)
# Emisor de tokens (como Pagatu: Keycloak en DEV, auth-ms en PROD): por defecto Keycloak (se levanta si no está) y el frontend en
# modo keycloak. Con --auth-propio los servicios confían en auth-ms (lo que necesitan los escenarios de tools/e2e: ./levantar.sh --auth-propio)
# y el frontend debe estar en `npm run auth:eduspace`.
AQUI="$(cd "$(dirname "$0")" && pwd)"; RAIZ="$(cd "$AQUI/../.." && pwd)"; LOGS="$AQUI/logs"; mkdir -p "$LOGS"
CT=${CONTENEDORES:-podman}
ARGS=(); AUTH_PROPIO=0
for a in "$@"; do [ "$a" = "--auth-propio" ] && AUTH_PROPIO=1 || ARGS+=("$a"); done
set -- "${ARGS[@]}"
if [ $AUTH_PROPIO = 1 ]; then
  export JWK_SET_URI=http://localhost:8090/.well-known/jwks.json
  echo "Emisor de tokens: auth-ms (login propio)"
else
  unset JWK_SET_URI   # el config-repo de DEV apunta a Keycloak
  curl -sf localhost:18090/realms/eduspace >/dev/null || "$RAIZ/infra/keycloak/levantar-dev.sh" || exit 1
  echo "Emisor de tokens: Keycloak (http://localhost:18090/realms/eduspace)"
fi
# Si el 19092 ya lo usa otro Kafka (p. ej. el de pagatu) se usa el 19093 para no pisarlo.
[ "$1" != "--sin-reset" ] && "$AQUI/detener.sh" >/dev/null 2>&1
KP=19092; ss -ltn 2>/dev/null | grep -q ":19092 " && [ "$1" != "--sin-reset" ] && KP=19093
[ -f "$AQUI/.kafka-port" ] && [ "$1" = "--sin-reset" ] && KP=$(cat "$AQUI/.kafka-port")
echo $KP > "$AQUI/.kafka-port"; export KAFKA_BOOTSTRAP_SERVERS=localhost:$KP
# App móvil desde otro equipo/celular: EXPONER_HOST=<IP del equipo> ./levantar.sh ... para que las URL firmadas de MinIO (subir tareas) sean alcanzables desde el teléfono.
[ -n "$EXPONER_HOST" ] && export MINIO_ENDPOINT="http://$EXPONER_HOST:19000"
JV="-Xmx256m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
if [ "$1" != "--sin-reset" ]; then
  "$AQUI/detener.sh" >/dev/null 2>&1
  $CT run -d --name e2e-kafka -p $KP:$KP \
   -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
   -e KAFKA_LISTENERS=INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:$KP,CONTROLLER://0.0.0.0:9093 \
   -e KAFKA_ADVERTISED_LISTENERS=INTERNAL://localhost:9092,EXTERNAL://localhost:$KP \
   -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT \
   -e KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
   -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
   -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
   -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=false \
   -e KAFKA_HEAP_OPTS="-Xmx384m -Xms256m" apache/kafka:4.3.1 >/dev/null
  $CT run -d --name e2e-minio -p 19000:9000 -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
   cgr.dev/chainguard/minio:latest server /data >/dev/null
  $CT run -d --name e2e-mailpit -p 11025:1025 -p 18025:8025 axllent/mailpit:v1.21 >/dev/null
  for par in auth:15440 planificacion:15441 alumnos:15442 matricula:15443 asistencia:15444 calificaciones:15445 pago:15446 examenes:15447 tareas:15448 notificaciones:15449; do
    n=${par%%:*}; p=${par##*:}
    $CT run -d --name e2e-pg-$n -p $p:5432 -e POSTGRES_DB=${n}_db -e POSTGRES_USER=eduspace -e POSTGRES_PASSWORD=eduspace \
     -e TZ=America/Lima postgres:16-alpine -c shared_buffers=32MB -c max_connections=60 >/dev/null
  done
  sleep 12
fi
arranca() { # nombre carpeta
  jar=$(ls "$RAIZ/$2"/target/*.jar | grep -v original | head -1)
  (cd "$RAIZ/$2" && nohup java $JV -jar "$jar" > "$LOGS/$1.log" 2>&1 &)
}
arranca config infra/eduspace-config
until curl -s localhost:18888/actuator/health | grep -q UP; do sleep 2; done; echo "config UP"
arranca eureka infra/eduspace-eureka
until curl -s localhost:18761/actuator/health | grep -q UP; do sleep 2; done; echo "eureka UP"
arranca auth services/auth-ms
until curl -s localhost:8090/actuator/health | grep -q UP; do sleep 2; done; echo "auth UP"
for s in planificacion alumnos notificaciones matricula asistencia tareas pago examenes calificaciones; do arranca $s services/$s-ms; done
arranca gateway infra/eduspace-gateway
echo "Arrancando... espere ~90 s y compruebe: for p in 8090 8091 8092 8093 8094 8095 8096 8097 8098 8099 18080; do curl -s localhost:\$p/actuator/health | head -c 40; echo; done"
