#!/bin/bash
# Detiene las aplicaciones y borra los contenedores de la prueba (e2e-*).
CT=${CONTENEDORES:-podman}
for p in 8090 18080 8091 8092 8099 8093 8094 8098 8096 8097 8095 18761 18888; do
  pid=$(ss -ltnp 2>/dev/null | grep ":$p " | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2); [ -n "$pid" ] && kill "$pid"
done
sleep 3
for c in $($CT ps -a --format '{{.Names}}' | grep '^e2e-'); do $CT rm -f "$c" >/dev/null; done
