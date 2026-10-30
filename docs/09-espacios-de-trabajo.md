# Menú por espacios de trabajo (2026-10-29)

El menú del personal tenía ~35 entradas sueltas. Ahora son **8 entradas** y cada una abre un *espacio de trabajo* con pestañas
que reúnen las pantallas que se usan juntas. Las URL no cambiaron (los enlaces y marcadores siguen valiendo).

| Entrada del menú | Pestañas |
|---|---|
| Matrícula | Matrículas · Alumnos · Importar alumnos · Campañas · Cupos |
| Aulas y secciones | **Panel de aulas (nuevo)** · Secciones · Aulas físicas |
| Cursos y docentes | **Panel de cursos (nuevo)** · Cursos · Malla curricular · Docentes · Asignaciones |
| Horarios | Armado automático · Horario por sección · **Aulas disponibles (nuevo)** · Bloques horarios |
| Año escolar | Años y periodos · Feriados |
| Asistencia y notas | Justificaciones · Reporte de asistencia · Cierre de periodo · Libretas y boletines |
| Finanzas | Resumen · Cuentas por alumno · Cobranza · Morosos · Conceptos* · Tarifas* · Descuentos* |
| Avisos y cuentas | Avisos y plantillas* · Cuentas de acceso* · Usuarios y roles* (las dos últimas, solo con el login propio) |

\* solo ADMIN. Docentes, familias y cajero conservan sus menús (ya eran cortos).

## Cómo está hecho
- `frontend/src/app/layout/hubs.ts` define los espacios y sus pestañas (con roles). `hubDe(url)` decide a qué espacio pertenece una URL (gana la ruta más específica; `/alumnos/12` cae en «Alumnos»).
- `shell.component` muestra la barra de pestañas sobre cualquier pantalla listada, resalta la entrada del menú y añade las pestañas a la búsqueda Ctrl+K.
- `layout/menu.ts` arma las entradas con `entradaHub(id)`. `layout/menu.spec.ts` comprueba que cada pestaña existe, que su rol puede abrirla y que ninguna ruta está en dos espacios.

## Pantallas nuevas (duplican funciones a propósito, para no ir de un lado a otro)
- **Panel de aulas** (`/planificacion/panel`): lista vertical de secciones y ficha con tutor, aula, cupos, asistencia, cursos/docentes. Desde la ficha: *Matricular alumno* (busca y deja en pre-matrícula), *Trasladar* (a otra sección del grado), *Asignar/Cambiar/Quitar docente*, *Editar sección*, *Nueva sección* (ADMIN), *Ver horario* y *Armar horario*.
- **Panel de cursos** (`/planificacion/cursos-docentes`): por curso o por docente, nuevo curso (con grado/horas de malla) y asignación de docente.
- **Aulas disponibles** (`/planificacion/aulas-disponibles`): mapa día × bloque con cuántas aulas están libres; al tocar un bloque, quién ocupa cada aula; semana completa de un aula; filtros de turno, tipo y capacidad; avisa de secciones sin aula.
- **Horario por sección**: al abrir una celda, los docentes y aulas ya ocupados en ese bloque salen marcados y deshabilitados.
- Backend: `GET /api/v1/planificacion/horarios/anio/{anioId}` (clases del año) en `planificacion-ms` (ADMIN y SECRETARIA).

## Verificado
`ng test` 55 pruebas OK · `ng build` OK · navegador (admin y secretaria): todas las pestañas sin errores 4xx/5xx; matricular, trasladar, crear y eliminar sección desde el panel (datos de prueba revertidos).
Las pruebas de integración de `planificacion-ms` necesitan Testcontainers (Docker); con Podman fallan por entorno, no por este cambio.
