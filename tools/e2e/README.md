# Pruebas de punta a punta (por consola)

Levanta todo el sistema en local y recorre el flujo completo por la API REST (sin front). Sirve para ver, con llamadas
reales, que los 10 servicios, Kafka, MinIO, Eureka, el Config Server y el Gateway trabajan juntos.

## Requisitos
- Podman o Docker (`CONTENEDORES=docker ./levantar.sh` si no es Podman), Java 21, Maven (usa `./mvnw` de cada proyecto) y Python 3 (solo biblioteca estándar).
- ~9 GB de RAM libres. Puertos: 8091-8099, 18080, 18761, 18888, 8090 (auth-ms), 15441-15449, 19092, 19000, 11025/18025.

## Uso
```bash
./compilar.sh      # una vez (o tras cambiar código)
./levantar.sh      # recrea bases y arranca todo (~2 min). Con --sin-reset solo reinicia las aplicaciones
./correr_todo.sh   # ejecuta 01 … 06; cada línea muestra OK / ERR; al final "TODOS LOS ESCENARIOS OK"
./detener.sh
```
Los escenarios usan bases nuevas (crean alumnos con DNI fijo); para repetirlos hay que volver a `./levantar.sh`.

## Qué recorre cada escenario
1. `01_planificacion_alumnos.py` — año, periodos, curso, docente, secciones (A con capacidad 2), asignación; 3 alumnos con apoderado y expediente completo (documentos en MinIO).
2. `02_matricula_pago.py` — tarifas, campaña, matrículas, tercer alumno rechazado por cupo, cuota de matrícula generada por Kafka.
3. `03_cobro_y_replicas.py` — caja, cobro idempotente (comprobante único), `pago.confirmado` → matrícula MATRICULADO → 10 pensiones, réplicas en asistencia/tareas/exámenes/calificaciones.
4. `04_asistencia_tareas_examenes.py` — pasar lista (una sola notificación de falta), justificación aprobada, tarea con archivo en MinIO y nota, examen rendido y calificado.
5. `05_calificaciones_boletin.py` — notas por eventos como pendientes, criterios, promedio ponderado, cierre, boletín PDF desde MinIO, libretas por dueño, reapertura.
6. `06_gateway_traslado_notificaciones.py` — rutas del Gateway, traslado de sección, retiro (anula cuotas), correos generados.

## Usuarios de prueba
Los tokens son reales: salen de `auth-ms` (`POST /api/v1/auth/login`). Las semillas de DEV son `admin/admin123`, `secretaria/secre123`
y `cajero/cajero123`; los demás usuarios (docente, alumno, apoderado…) los crea `lib.py` con la API de administración la primera vez
que se usan (clave `Clave12345`). Para obtener un token a mano:
`curl -s localhost:8090/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'`.
