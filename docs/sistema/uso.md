# Uso del sistema

## Flujo principal (de punta a punta)
1. **Planificación** — el ADMIN crea el año y los periodos, secciones por grado, aulas, cursos y malla, y registra docentes (*Aulas y secciones*, *Cursos y docentes*, *Año escolar*).
2. **Alumnos** — se registran (o se importan por CSV) con apoderados, documentos y ficha de salud (*Matrícula → Alumnos*).
3. **Matrícula** — también puede hacerla la propia familia desde el sitio público ([Matrícula en línea](matricula-en-linea.md)); o la secretaría: se abre una campaña, se matricula al alumno en una sección (pre-matrícula) y el cupo se descuenta (*Matrícula* o botón «Matricular alumno» en el Panel de aulas). **En ambos casos, tras el pago la matrícula queda «por aprobar» y el administrador la aprueba en *Matrícula → Por aprobar*** (así se matricula al alumno y se generan las pensiones).
4. **Cobro** — el cajero cobra la matrícula; el pago confirma la matrícula y genera las 10 pensiones.
5. **Horarios** — *Horarios → Armado automático* (simular, generar, validar, publicar); los docentes y familias reciben aviso.
6. **Día a día** — el docente pasa lista, publica tareas y exámenes; la familia ve horario, tareas y pagos.
7. **Cierre** — el docente registra notas con criterios; el personal cierra el periodo y genera el boletín PDF.

## Casos de uso por rol

| Rol | Pantallas y acciones |
|---|---|
| **ADMIN** | Todo: inicio con estadísticas y aulas, planificación, matrícula, asistencia y notas, finanzas (incluye conceptos, tarifas y descuentos), avisos y plantillas, usuarios |
| **SECRETARIA** | Igual que ADMIN salvo configuración (conceptos/tarifas/descuentos, plantillas, usuarios); edita horarios, asignaciones y docentes; matricula, traslada, atiende justificaciones |
| **DOCENTE** | **Mis cursos** (tarjetas por curso y sección; dentro de cada uno: tareas con entregas, alumnos y exámenes), horario, asistencia, exámenes y banco de preguntas, notas |
| **APODERADO** | **Mis cursos** de cada hijo (tareas, notas y exámenes por curso), horario, asistencia, notas y boletines, **pagos** |
| **ALUMNO** | Lo mismo que el apoderado salvo pagos; entrega tareas y rinde exámenes desde cada curso |
| **CAJERO** | Caja: abrir/cerrar sesión, cobrar, comprobante |
| **Público** | Sitio del colegio: inicio, nosotros, niveles, admisión, noticias, contacto y **matrícula en línea** (reserva, pago, aprobación del colegio y constancia por correo; ver [Matrícula en línea](matricula-en-linea.md)) |

Mapa completo del menú del personal: [Frontend](frontend.md).

## Probar por API (Gateway)
```bash
# token en DEV (cliente de pruebas, solo localhost)
TOKEN=$(curl -s -d grant_type=password -d client_id=eduspace-e2e -d username=<usuario> -d password=<clave> \
  localhost:18090/realms/eduspace/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
curl -H "Authorization: Bearer $TOKEN" localhost:18080/api/v1/planificacion/anios/activo
curl -H "Authorization: Bearer $TOKEN" "localhost:18080/api/v1/matriculas/cupos?anioId=1"
```
Cada servicio expone Swagger en su puerto. Todos los listados paginan con `page` y `size`.

## Escenarios guiados de punta a punta
`tools/e2e/correr_todo.sh` (con `levantar.sh --auth-propio`) ejecuta seis recorridos con llamadas reales:

| # | Escenario | Comprueba |
|---|---|---|
| 1 | planificación y alumnos | año, secciones, asignación, 3 alumnos con apoderado y documentos en MinIO |
| 2 | matrícula y pago | tarifas, campaña, **cupo agotado** rechaza al tercero, cuota generada por Kafka |
| 3 | cobro y réplicas | cobro idempotente, `pago.confirmado` → POR_APROBAR → aprobación (ADMIN) → MATRICULADO → 10 pensiones, réplicas locales |
| 4 | asistencia, tareas, exámenes | una sola notificación por falta, justificación, entrega con archivo, examen calificado |
| 5 | calificaciones y boletín | notas por evento, promedio ponderado, cierre, boletín PDF, libretas por dueño |
| 6 | Gateway, traslado, notificaciones | 401/403, traslado y retiro (cuotas anuladas), correos en Mailpit |
