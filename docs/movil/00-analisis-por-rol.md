# App móvil: análisis por rol

La app móvil de EduSpace es **una sola aplicación** (Flutter) que cambia de pantallas según el rol de la cuenta. Docentes, alumnos y apoderados ingresan con el mismo usuario y clave de la web. El personal administrativo (admin, secretaría, caja) **no** usa la app: sus tareas son de escritorio (formularios largos, tablas, caja).

## Qué necesita cada persona en el celular

La regla para decidir qué entra a la app: *¿se hace de pie, en el pasillo o en cinco minutos?* Si sí, va al móvil. Si requiere tablas, importar archivos o configurar, se queda en la web.

| Rol | Lo que hace todos los días | Pantalla |
|---|---|---|
| **Docente** | Ver qué clase le toca ahora; **pasar lista** en el aula; dejar una **tarea** al terminar la clase; calificar entregas; enterarse de avisos | Hoy · Cursos · Horario · Perfil |
| **Alumno** | Saber qué tiene hoy y qué debe entregar; **entregar tareas** (foto o archivo); ver horario, notas y su asistencia | Inicio · Tareas · Horario · Notas · Más |
| **Apoderado** | Saber cómo va su hijo (asistencia, tareas pendientes); **justificar** una inasistencia; ver notas y boletines; **pagar** cuotas | Inicio · Tareas · Horario · Notas · Más (Asistencia, Pagos) |

Con más de un hijo, el apoderado ve un selector arriba y todas las pantallas cambian de hijo.

### Docente

* **Hoy**: las clases del día en orden, con la que está en curso marcada. Tocar una clase abre **Pasar lista** de esa sección.
* **Pasar lista**: un renglón por alumno con cuatro botones (P presente, T tarde, F falta, J justificado), «Todos presentes», *Guardar* y *Guardar y cerrar*. Al cerrar, el registro queda bloqueado como en la web.
* **Cursos → curso**: tres pestañas, *Tareas*, *Materiales* y *Asistencia*. En *Materiales* el docente sube **archivos** (PDF, Word, PowerPoint, Excel, imágenes, ZIP o texto, hasta 10 MB) o **enlaces** del curso, y los elimina; la familia recibe un aviso. En *Tareas* se crea con «Nueva tarea» (título, instrucciones, fecha y hora, puntaje, tardías, publicar ahora) y se entra a cada tarea para ver quién entregó y **calificar** (nota + retroalimentación). Publicar avisa a alumnos y apoderados.
* **Horario**: la semana por día.

### Alumno

* **Inicio**: pendientes, % de asistencia, clases de hoy y las próximas tareas.
* **Tareas**: lista con estado (sin entregar, entregada, calificada, devuelta). Tocar una tarea pendiente abre **Entregar**: adjuntar archivos desde el teléfono (PDF, Word, imágenes, texto; máx. 10 MB c/u) y un comentario. Los archivos suben directo al almacenamiento con una URL firmada, igual que en la web.
* **Horario** y **Notas** (con boletines en PDF). En **Más**: **materiales** de sus docentes (agrupados por curso, se abren o descargan), asistencia, exámenes disponibles y perfil.

### Apoderado

Lo mismo que el alumno, pero **sin entregar tareas** (las entrega el alumno) y con:

* **Asistencia**: resumen y últimos registros, y **«Justificar una inasistencia»** (motivo médico/familiar/otro, rango de fechas, detalle).
* **Pagos**: saldo pendiente, cuotas con vencimiento y atraso y botón **Pagar** (pasarela simulada, medio `ONLINE_SIMULADO`, con clave de idempotencia para que un doble toque no cobre dos veces).

## Notificaciones (todos los roles)

* Campana en la barra superior con **contador de no leídas**.
* Bandeja con paginación, filtro «solo sin leer», tocar para marcar leída y «marcar todas».
* El contador se actualiza cada 45 s mientras la app está abierta y al volver a primer plano.
* Lo que llega depende de los eventos del sistema (nueva tarea, tarea por vencer, notas, asistencia, cuotas, etc.): son los mismos de la web, porque la bandeja es la misma (`notificaciones-ms`).

!!! warning "Notificación «push» real"
    Que el celular vibre con la app cerrada exige Firebase Cloud Messaging (credenciales del proyecto de Google) y registrar el token del dispositivo en `POST /api/v1/notificaciones/dispositivos`, que el servicio ya expone. Sin credenciales no se puede probar ni entregar, por eso esta versión usa consulta periódica. Ver [Configuración](01-app-movil.md#notificaciones-push-reales).

## Para todos

* **Ingreso** con usuario y clave; si la cuenta es nueva o fue restaurada por secretaría, la app **obliga a crear una clave** antes de continuar.
* **Perfil**: cambiar clave y cerrar sesión.
* **Sesión vencida o sistema reiniciado**: la app renueva el token una vez; si no es posible, vuelve al ingreso con el aviso correspondiente. Si el servicio no responde, muestra una franja «El sistema no responde por ahora» y un botón **Reintentar** en cada pantalla, sin cerrar la sesión.

## Qué se dejó para la web (a propósito)

Rendir exámenes con temporizador, banco de preguntas, matrícula, horarios y todo lo administrativo. En la app, los exámenes solo se **listan** («disponible hasta…») para que nadie se los pierda.
