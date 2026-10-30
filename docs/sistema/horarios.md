# Horarios

Los horarios los gestiona `planificacion-ms`; los consumen docentes, familias y notificaciones.

## Modelo
| Concepto | Qué es |
|---|---|
| **Bloque horario** | Franja del día de un turno (mañana/tarde) con orden, hora de inicio y fin; puede ser **receso** |
| **Asignación** | Docente ↔ curso ↔ sección (una por curso y sección) |
| **Clase (horario)** | Una asignación puesta en un día (lunes–viernes) y un bloque, con aula |
| **Publicación** | Versión publicada del horario de una sección; al publicar se avisa a docentes y familias |

Ejemplo medido: turno mañana con 5 bloques de clase (45 min, 08:00 a 12:05) y un receso 10:15–10:35 = 25 espacios por sección y semana; la malla de Primaria pide 24 horas, es decir **288 clases** para 12 secciones.

## Reglas (el sistema las exige siempre)
1. El **docente** no puede estar en dos secciones a la misma hora.
2. El **aula** no puede estar ocupada dos veces.
3. La **sección** no puede tener dos clases a la vez.
4. El bloque debe ser del **mismo turno** que la sección.
5. No se programa en un **receso**.
6. El aula debe tener **aforo** para la sección (si ambos lo declaran).
7. La validación de la sección marca los cursos con **horas faltantes o sobrantes** respecto de la malla curricular.
Un año CERRADO es de solo lectura.

## Cómo se arma (pantallas del espacio *Horarios*)
| Pestaña | Para qué |
|---|---|
| **Armado automático** | 3 pasos: (1) malla de cursos y horas por grado en lote; (2) ver qué secciones están listas y qué les falta; (3) **simular** o **generar** (con asignación de docentes por especialidad opcional) |
| **Horario por sección** | Grilla día × bloque; al abrir una celda se elige curso y docente y, opcionalmente, aula; los docentes y aulas ocupados en ese bloque salen marcados y bloqueados; validar y **publicar** |
| **Aulas disponibles** | Mapa día × bloque con cuántas aulas están libres; quién ocupa cada aula; semana de un aula; filtros de turno, tipo y capacidad |
| **Bloques horarios** | Crear y editar franjas y recesos |

Atajos: desde el *Panel de aulas*, «Ver horario» y «Armar horario» de la sección elegida.

## Algoritmo del armado automático
- **Entrada:** secciones elegidas, malla (horas por curso), docentes asignados, bloques del turno y lo ya ocupado.
- **Espacio de búsqueda:** cada clase a colocar ocupa una celda *día × bloque*; restricciones **duras** de sección, docente y aula (nunca se violan).
- **Estrategia:** se ordenan las clases por dificultad (*MRV*: menos opciones primero), se coloca con búsqueda voraz y, si queda algo sin ubicar, se mejora con *min-conflicts* y **lista tabú**; semilla fija (2026) para resultados repetibles.
- **Docentes:** con `asignarDocentes`, se eligen por **especialidad** (prioridad 0: coincide; 1: generalista de Primaria/Inicial; 2: no elegible).
- **Simulación:** ejecuta el mismo código y termina con `setRollbackOnly` (no guarda).
- **Resultado medido** (Primaria): 12 secciones, 288 de 288 horas colocadas, 0 sin colocar, 0 cruces; **0,28–0,63 s**.

## Quién ve qué
| Rol | Vista |
|---|---|
| ADMIN / SECRETARIA | Todos los horarios; editan (SECRETARIA también) y publican |
| DOCENTE | *Mi horario*: sus clases de la semana |
| ALUMNO / APODERADO | *Horario* de la sección del alumno (o de cada hijo) |

## API (`/api/v1/planificacion/horarios`)
| Endpoint | Uso |
|---|---|
| `GET seccion/{id}` · `GET docente/{id}` · `GET anio/{id}` | Clases por sección, por docente o de todo el año |
| `GET preparacion?anioId=` | Qué secciones están listas |
| `POST generar` | Generar o simular (`simular=true`) |
| `POST /` · `PUT {id}` · `DELETE {id}` | Agregar, mover y quitar una clase (con validación de choques) |
| `GET validar/{seccionId}` | Choques y cursos incompletos |
| `POST publicar/seccion/{id}` · `GET publicar/seccion/{id}` | Publicar e historial de versiones (`horario.publicado`) |

## Estado de los datos de demostración
Primaria: malla, docentes y 288 clases generadas (sin publicar). Inicial y Secundaria: falta cargar cursos y docentes; el mismo procedimiento los cubre. Ver [Limitaciones](limitaciones.md).
