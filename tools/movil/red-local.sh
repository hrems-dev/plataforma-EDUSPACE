#!/usr/bin/env bash
# Muestra la dirección con la que la app móvil (otro equipo o celular en la misma red) llega al Gateway de este equipo.
IP=$(ip -4 route get 1.1.1.1 2>/dev/null | grep -oP 'src \K[0-9.]+' | head -1)
[ -z "$IP" ] && IP=$(hostname -I 2>/dev/null | awk '{print $1}')
echo "IP de este equipo en la red:  $IP"
echo "En la app → Servidor:          http://$IP:18080"
echo
if curl -s -m 3 "http://$IP:18080/actuator/health" | grep -q '"status":"UP"'; then
  echo "✔ El Gateway responde en esa dirección."
else
  echo "✘ El Gateway no responde en http://$IP:18080 (¿está levantado el sistema?)."
fi
if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  firewall-cmd --list-ports | grep -q 18080 || echo "! Cortafuegos activo sin el puerto 18080. Ábrelo (temporal):  sudo firewall-cmd --add-port=18080/tcp --add-port=19000/tcp"
fi
echo
echo "Para subir tareas desde el celular, levanta el sistema con:  EXPONER_HOST=$IP tools/e2e/levantar.sh --auth-propio --sin-reset"
