#!/bin/bash
# Ejecuta los 6 escenarios en orden (el entorno debe estar arriba y con bases nuevas). Termina con código 1 si algo falla.
cd "$(dirname "$0")" || exit 1
# Eureka propaga el registro cada pocos segundos: se da un margen tras el arranque (SIN_ESPERA=1 para omitirlo).
[ -z "$SIN_ESPERA" ] && sleep 20
rc=0
for f in 01_*.py 02_*.py 03_*.py 04_*.py 05_*.py 06_*.py; do
  echo; echo "################ $f"; python3 "$f" 2>&1 | tee "logs/${f%.py}.txt" | tail -n +1
  grep -q "FAILS: \[\]" "logs/${f%.py}.txt" || rc=1
done
[ $rc -eq 0 ] && echo "TODOS LOS ESCENARIOS OK" || echo "HAY FALLAS (ver arriba)"
exit $rc
