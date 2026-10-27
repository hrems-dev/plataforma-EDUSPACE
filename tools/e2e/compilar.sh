#!/bin/bash
# Compila (sin pruebas) los jars de infraestructura y de los 10 servicios con base de datos.
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
for d in infra/eduspace-config infra/eduspace-eureka infra/eduspace-gateway services/{auth,planificacion,alumnos,notificaciones,matricula,asistencia,tareas,pago,examenes,calificaciones}-ms; do
  echo "== $d"; (cd "$RAIZ/$d" && ./mvnw -q -o clean package -DskipTests 2>&1 | tail -5)
done
