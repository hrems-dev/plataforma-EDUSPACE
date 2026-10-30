#!/usr/bin/env python3
"""Genera docs/presentacion/guion-por-diapositiva.pdf: lo que cada integrante dice en cada diapositiva.

Uso:  python3 tools/presentacion/guion_diapositivas.py      (necesita Google Chrome para imprimir el PDF)
El contenido sigue la numeración de docs/presentacion/04-diapositivas-para-generador.md.
"""
import html, os, subprocess, sys, tempfile

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SALIDA = os.path.join(RAIZ, 'docs', 'presentacion', 'guion-por-diapositiva.pdf')

COLOR = {'Hermes': '#1F3A87', 'Andre': '#E8890C', 'Witmer': '#0B5D41', 'Todos': '#4B5875'}

# (n.º, título, quién, minutos, qué decir, qué mostrar, puente)
DIAPOSITIVAS = [
 (1, 'Portada · EduSpace', 'Hermes', '0:30',
  'Buenos días. Somos Hermes, Andre Fabricio y Witmer, y les presentamos EduSpace: un sistema de gestión escolar distribuido para el colegio Horizonte del Valle. '
  'Son diez microservicios que trabajan juntos. Cada uno de nosotros defenderá tres servicios y un proceso transaccional de su parte, con demostración en vivo.',
  'Portada. Nada más; mirar a la clase.', 'Empiezo por el problema que queríamos resolver.'),
 (2, 'El problema · El colegio vive en hojas sueltas', 'Hermes', '1:00',
  'En un colegio, la matrícula, la asistencia, las notas, los cobros y los horarios suelen vivir en listas separadas. Un mismo alumno existe en cinco listas distintas, '
  'y cuando una cambia, las otras no se enteran. Las familias se enteran tarde y cada área tiene su propia verdad. Ese es el problema de fondo: datos duplicados y desfasados.',
  'Diagrama de cinco cajas desconectadas con flechas rotas.', 'Veamos cómo lo resolvemos.'),
 (3, 'La solución · Una verdad por dato, eventos para el resto', 'Hermes', '1:00',
  'Nuestra idea: cada dato tiene un solo dueño. Son diez microservicios con base de datos propia. Se comunican de dos maneras: consultas internas síncronas y eventos por Kafka. '
  'Todo entra por un Gateway único, y hay un portal por rol y un sitio público. Ningún servicio lee la base de otro: lo que necesita le llega por evento o por consulta interna.',
  'Las mismas cinco cajas, ahora unidas por una línea central (Kafka) y el Gateway arriba.', 'Ahora los números, para dimensionar el sistema.'),
 (4, 'EduSpace en cifras', 'Hermes', '0:45',
  'Son diez servicios más Config Server, Eureka y el Gateway. Superamos los 300 endpoints, tenemos 25 tópicos de Kafka, cerca de 280 pruebas de backend y 65 de frontend, '
  'y auditamos más de cien pantallas. Las cifras están medidas y documentadas; el detalle está en la documentación de métricas.',
  'Cinco tarjetas con número grande. No leer todas: resaltar servicios, tópicos y pruebas.', 'Veamos cómo encajan las piezas.'),
 (5, 'Arquitectura distribuida', 'Hermes', '1:15',
  'El navegador, o la app móvil, solo habla con el Gateway, en el puerto 18080. El Gateway valida el token JWT, aplica límites por IP y enruta a los servicios. '
  'Eureka descubre dónde está cada servicio y el Config Server centraliza la configuración. Cada servicio tiene su PostgreSQL, los archivos van a MinIO y los cambios se comunican por Kafka. '
  'Y cada flecha tiene un contrato: HTTP interno con un token de servicio, o un evento con sobre estándar.',
  'Señalar con el puntero: Navegador → Gateway → servicios; luego Kafka al centro y las bases abajo.', 'Ahora, cómo repartimos el trabajo.'),
 (6, 'Reparto del equipo · Quién defiende qué', 'Hermes', '0:30',
  'Hermes defiende planificación, alumnos y notificaciones, además de las cuentas de acceso. Andre Fabricio, matrícula, asistencia y tareas. Witmer, pago, exámenes y calificaciones. '
  'La infraestructura, la seguridad y la observabilidad son de los tres. Cada uno mostrará un proceso transaccional de su parte.',
  'Tabla de tres columnas con la cinta de color de cada uno.', 'Antes de entrar en cada parte, una idea que nos une a los tres.'),
 (7, 'Las tres ideas de consistencia', 'Hermes', '1:00',
  'Como cada servicio tiene su base, no hay una transacción global. Mantenemos la consistencia con tres ideas. Una: el outbox, el dato y su evento se guardan en la misma transacción. '
  'Dos: la idempotencia, repetir una operación no la duplica. Tres: la compensación, si algo sale mal se deshace con otro paso, no con un rollback global. '
  'Es la consistencia distribuida con transacciones locales, eventos y compensaciones. Cada proceso que van a ver usa estas tres ideas.',
  'Tres iconos grandes: caja de salida, doble check y flecha de deshacer.', 'Empiezo yo: planificación, alumnos y notificaciones.'),

 (8, 'Hermes · planificación, alumnos, notificaciones', 'Hermes', '1:30',
  'Mi parte. Planificación guarda el año escolar, las secciones, la malla curricular, los docentes y los horarios, y es la fuente de verdad que casi todos consultan. '
  'Alumnos guarda el expediente, los apoderados y los documentos. Notificaciones se ocupa de la bandeja, el correo y las plantillas. Y el servicio de autenticación crea las cuentas con el formato nombre.apellido. '
  'Aquí se ven el Panel de aulas y el Armado automático de horarios.',
  'Capturas del Panel de aulas y del Armado automático, lado a lado.', 'Mi proceso transaccional: publicar un horario «todo o nada».'),
 (9, 'Publicar un horario «todo o nada»', 'Hermes', '2:00',
  'Generar un horario toca cientos de filas, y no puede quedar a medias. El flujo es: primero simular, que es la misma transacción pero revertida, así que nada se guarda. '
  'Luego generar sin cruces, validar y publicar. Si hay un choque, por ejemplo un docente con dos clases a la misma hora, el sistema responde 409 y no deja nada a medias. '
  'Al publicar, la nueva versión del horario y el evento horario.publicado se guardan en una sola transacción, y Kafka lo entrega a notificaciones, que avisa a docentes y familias.',
  'Flujo: Simular (etiqueta roja «nada se guarda») → Generar → Validar → Publicar → versión + evento → Kafka → avisos.', 'Les muestro el código que lo hace posible.'),
 (10, 'El código clave', 'Hermes', '1:30',
  'Dos fragmentos. En el generador de horarios, cuando se pide simular, marcamos la transacción como solo-rollback: se hace todo el cálculo, pero al final se deshace. '
  'Y en el registrador del outbox, un listener que corre justo antes del commit inserta el evento en la tabla outbox_eventos, dentro de la misma transacción del dato. '
  'Si el commit falla, el evento tampoco existe; si el commit funciona, el evento está garantizado.',
  'Abrir en el editor la línea 144 del generador y luego el OutboxRecorder. Resaltar setRollbackOnly y BEFORE_COMMIT.', 'Vamos a verlo funcionar.'),
 (11, 'Demostración en vivo', 'Hermes', '2:00',
  'Ejecuto el script del proceso. Primero simula: coloca 288 clases en 12 secciones y, al terminar, la base sigue en 288, sin cambios. Después intenta duplicar una clase del mismo docente en el mismo bloque y recibe el 409: '
  'las clases de la sección siguen en 24, nada a medias. Por último publica: se crea la versión 2 y el evento queda en el outbox. Ahora abro el portal y muestro el aviso que recibe el docente.',
  'Terminal: python3 tools/demo/proceso_hermes.py. Resaltar «SIN CAMBIOS (rollback)», «HTTP 409» y «versión publicada». Luego mostrar el aviso en Notificaciones.', 'Resumo lo que aprendimos.'),
 (12, 'Qué aprendimos (Hermes)', 'Hermes', '1:00',
  'Tres lecciones. El rollback nos permite simular con seguridad: lo que veo es lo que se guardaría. El outbox evita perder avisos aunque Kafka esté caído, porque el evento espera en la base. '
  'Y los consumidores deben ser idempotentes, porque un mismo evento puede llegar más de una vez. Medimos 12 secciones, 288 clases, cero cruces, en menos de un segundo. Le paso la palabra a Andre.',
  'Frase destacada: «Lo que veo es lo que se guardaría.»', 'Andre, tu turno.'),

 (13, 'Andre · matrícula, asistencia, tareas', 'Andre', '1:30',
  'Gracias, Hermes. Yo defiendo matrícula, asistencia y tareas. Matrícula maneja las campañas, los cupos, los estados, el traslado y el retiro, y es la dueña de la relación alumno–sección. '
  'Asistencia lleva sesiones, justificaciones y reportes. Tareas, las entregas con archivos y la calificación de 0 a 20. Como no pueden consultar a matrícula en cada petición, asistencia y tareas guardan una réplica local, matriculas_local, que se alimenta por eventos.',
  'Capturas de Cupos y del curso del docente (pestaña Tareas).', 'Mi proceso: la matrícula con cupo atómico, pago, aprobación y compensación.'),
 (14, 'Matrícula: cupo atómico, pago y compensación', 'Andre', '2:30',
  'Es una saga coreografiada: cada paso es una transacción local y los servicios reaccionan a eventos. Primero, una transacción reserva el cupo y crea la pre-matrícula, y publica matricula.creada. '
  'Pago crea la cuota y cobra. Cuando llega pago.confirmado, la matrícula pasa a POR_APROBAR: ya pagó, el cupo está reservado, pero todavía no es una matrícula. Entonces el administrador la revisa y la aprueba; recién ahí pasa a MATRICULADO y se publica matricula.confirmada, que genera las diez pensiones y habilita al alumno en asistencia y tareas. '
  'Y las compensaciones, en rojo: si la reserva vence sin pago, o se anula, o se retira, se publica el evento y el cupo vuelve y las cuotas pendientes se anulan.',
  'Diagrama de secuencia en tres carriles (matrícula · Kafka · pago). Señalar el recuadro de la aprobación del administrador y las flechas rojas punteadas.', 'Veamos el código que protege el cupo.'),
 (15, 'El código clave', 'Andre', '1:30',
  'Dos ideas. La primera: reservar el cupo es una sola sentencia, UPDATE cupos_seccion SET ocupados = ocupados + 1 WHERE ocupados < capacidad. Comprueba y reserva a la vez; si ya no hay lugar, devuelve cero filas y respondemos 409. No hay carrera por la última vacante. '
  'La segunda: el servicio del outbox está marcado con Propagation.MANDATORY, es decir, obliga a que el evento viaje dentro de la transacción del dato; no se puede publicar por fuera.',
  'Abrir CupoSeccionRepository (resaltar el WHERE) y EventoOutboxService (resaltar MANDATORY).', 'Lo demuestro con el script.'),
 (16, 'Demostración en vivo', 'Andre', '2:30',
  'Ejecuto el proceso. Inicia una matrícula en línea y reserva el cupo; se ve cuántos cupos ocupados hay. Pago con la tarjeta de rechazo: HTTP 402, no se cobra y la reserva sigue intacta. '
  'Pago con la tarjeta buena: la matrícula queda POR_APROBAR, sin cuentas ni pensiones todavía; repetir el pago devuelve el mismo comprobante, no cobra dos veces. '
  'Secretaría intenta aprobar y recibe 403; el administrador aprueba, y ahora sí: cuentas, diez pensiones y el correo con el acceso, que vemos en Mailpit. Por último, retiro: el cupo vuelve.',
  'Terminal: AUTH_MODE=eduspace python3 tools/demo/proceso_andre.py. Resaltar 402, POR_APROBAR, 403 de secretaría, «pensiones=10» y los cupos antes/después. Luego Kafka UI y Mailpit.', 'Cierro con lo aprendido.'),
 (17, 'Qué aprendimos (Andre)', 'Andre', '1:00',
  'La concurrencia se resuelve en la base de datos, no en el código: el cupo es una sentencia, no una esperanza. La idempotencia hace que el pago repetido no duplique nada. '
  'Y lo que no se paga, se libera: la reserva vence y devuelve el cupo. Lo probamos con dos hilos peleando por el último cupo, pagos repetidos, vencimiento y Kafka caído. Witmer continúa con el dinero.',
  'Frase destacada: «El cupo es una sentencia, no una esperanza.»', 'Witmer, tu turno.'),

 (18, 'Witmer · pago, exámenes, calificaciones', 'Witmer', '1:30',
  'Gracias, Andre. Yo defiendo pago, exámenes y calificaciones. Pago es el dueño del dinero: conceptos, tarifas, cuotas, caja, comprobantes y mora; aquí un error cuesta caro. '
  'Exámenes controla el intento con el tiempo del servidor, y nunca envía al navegador cuál es la respuesta correcta. Calificaciones arma los criterios y promedios, el cierre de periodo y el boletín en PDF; las familias solo ven los periodos cerrados.',
  'Capturas de Finanzas → Resumen y de la libreta/boletín.', 'Mi proceso: el cobro idempotente.'),
 (19, 'Cobro idempotente con comprobante y evento', 'Witmer', '2:30',
  'Un cobro llega con una clave de idempotencia. Primero pregunto: ¿ya existe un pago con esta clave? Si sí, devuelvo el mismo pago, no cobro de nuevo; así un reintento de red no duplica. '
  'Si no existe, todo ocurre en una sola transacción: valido, registro el pago y su detalle, actualizo los saldos con control de versión, tomo el siguiente número de comprobante con un bloqueo, y guardo el evento en el outbox. '
  'Y la compensación: anular un pago no borra nada; revierte los saldos, anula el comprobante y publica pago.anulado para que matrícula y notificaciones se enteren.',
  'Flujo: POST /pagos + idempotencyKey → «¿ya existe?» → recuadro «UNA transacción» → Kafka. Abajo, flecha roja de Anular.', 'Les muestro tres fragmentos de código.'),
 (20, 'El código clave', 'Witmer', '1:30',
  'Tres ideas en tres líneas. Una: findByIdempotencyKey, si ya existe devuelvo ese pago. Dos: findFirstByTipoActivaForUpdate, bloquea la fila de la serie del comprobante mientras tomo el número, así dos cobros simultáneos no repiten el mismo, y no hay huecos. '
  'Tres: en la anulación, cuota.setSaldo(saldo + montoAplicado): la compensación restituye el saldo.',
  'Abrir PagoService, ComprobanteService y AnulacionService; resaltar las tres líneas.', 'Vamos a ejecutarlo.'),
 (21, 'Demostración en vivo', 'Witmer', '2:00',
  'Ejecuto el script con la caja abierta. Primero un cobro: sale el comprobante y baja el saldo. Repito con la misma clave: mismo pago, mismo comprobante, el saldo no cambia. '
  'Intento cobrar más que el saldo: 409 y el saldo queda intacto. Y anulo el pago: el saldo se restituye y se publica pago.anulado.',
  'Terminal: AUTH_MODE=eduspace python3 tools/demo/proceso_witmer.py. Resaltar «mismo pago: True», el 409 y «saldo restituido». Después cerrar la caja desde la pantalla Caja.', 'Cierro con las lecciones.'),
 (22, 'Qué aprendimos (Witmer)', 'Witmer', '1:00',
  'La clave de idempotencia protege del reintento; el bloqueo del correlativo evita comprobantes duplicados; y anular no borra, compensa. Cobrar dos veces no es un error de red: es un error de diseño. '
  'Lo probamos con dos cobros simultáneos, comprobante único y anulación. Ahora vamos a ver todo junto.',
  'Frase destacada: «Cobrar dos veces no es un error de red: es un error de diseño.»', 'Los tres: el proceso integrado.'),

 (23, 'Matrícula en línea, de punta a punta', 'Todos', '3:00',
  'Andre: «Una familia entra al sitio público, sin sesión, elige el grado y llena los datos; se reserva el cupo.» '
  'Witmer: «Paga con la pasarela simulada; el cobro es idempotente y genera su comprobante.» '
  'Hermes: «El pago deja la matrícula en revisión. El administrador la aprueba y entonces se crean las cuentas con nombre.apellido, se generan las pensiones y se publica el horario.» '
  'Andre: «Y la familia recibe un correo con su usuario, la clave inicial, la constancia en PDF, el horario y las finanzas. Al entrar por primera vez, el sistema le pide cambiar la clave.»',
  'En el navegador: sitio público → pagar → pantalla «en revisión» → (otra pestaña, admin) Matrícula → Por aprobar → Aprobar → Mailpit con el correo → ingresar con la cuenta nueva.', 'Veamos cómo lo protegemos.'),
 (24, 'Seguridad y resiliencia', 'Hermes', '1:30',
  'Los tokens JWT los emite Keycloak o nuestro servicio de autenticación. Los endpoints internos solo aceptan un token de servicio. El Gateway limita las peticiones por IP, con un cupo especial para el sitio público. '
  'En desarrollo la clave de firma es persistente, y si un servicio se reinicia o la sesión deja de valer, la web muestra la franja «servicio caído» y lleva de vuelta al inicio de sesión, en lugar de quedarse con datos viejos.',
  'Candado y escudo; captura de la franja de aviso. Si da tiempo: reiniciar auth-ms con la sesión abierta y mostrar que el token sigue valiendo.', 'Y los números que lo respaldan.'),
 (25, 'Lo medimos', 'Witmer', '1:30',
  'La latencia mediana de las consultas está entre 10 y 70 milisegundos. El horario de 288 clases se genera en 0,3 a 0,6 segundos. El lag de Kafka es cero. '
  'Tenemos cerca de 280 pruebas de backend y 65 de frontend, y auditamos más de cien pantallas. Son mediciones en entorno de desarrollo; las cifras y su método están en la documentación de métricas.',
  'Tabla pequeña de latencias con indicadores verdes.', 'Cerramos con lo que falta.'),
 (26, 'Lo que falta y lo que sigue', 'Todos', '2:00',
  'Hermes: «Falta una pasarela de pago real y el correo real por SMTP.» Andre: «Falta el despliegue en producción con pruebas de carga y definir el prorrateo de pensiones cuando se matricula a mitad de año.» '
  'Witmer: «Y reembolsos automáticos al rechazar una matrícula.» Todos: «Para cerrar: una familia matricula desde la web y todo ocurre por transacciones locales y eventos; si algo falla, el sistema compensa. Gracias, ¿preguntas?»',
  'Línea «Hoy / Siguiente» y el contacto del equipo.', 'Preguntas (ver la hoja de respuestas).'),
]

DEMO_RAPIDA = [
 ('1', 'Proceso 1 · Nuevo alumno y aprobación', 'Andre', '3:00',
  'Una familia nueva quiere matricular a su hija. Entra al sitio, elige grado, llena los datos y paga con la tarjeta de prueba. Se reserva la vacante y la página dice «Pago recibido, tu matrícula está en revisión»: todavía no hay usuario ni clave. '
  'Llega el primer correo, de pago recibido. Ahora el administrador, en Matrícula → Por aprobar, revisa el origen, el pago y el expediente, y aprueba. Solo el administrador puede: secretaría recibe 403. '
  'Llega el segundo correo: matrícula aprobada, con un recuadro «Para iniciar sesión», la constancia en PDF, el horario y las pensiones. Entro con el usuario nuevo y el sistema me pide cambiar la clave.',
  'Sitio público → Mailpit → Matrícula → Por aprobar → Mailpit → login con la cuenta nueva.', 'Pasemos al aula.'),
 ('2', 'Proceso 2 · Profesor en clase', 'Hermes', '2:00',
  'El docente abre su app y ve la clase de hoy, que viene del horario que publicó planificación. La toca y pasa lista: presente para todos y una falta. Guarda y cierra; queda bloqueada. '
  'Luego sube un material del curso, un PDF, y crea y publica una tarea. La familia recibe el aviso en el momento.',
  'App móvil del docente: Hoy → Pasar lista → Cursos → Materiales → Nueva tarea.', 'Ahora la alumna.'),
 ('3', 'Proceso 3 · Alumna: tarea y examen', 'Witmer', '2:00',
  'La alumna ve la tarea, adjunta un archivo y la entrega; el archivo sube directo al almacenamiento con una URL firmada. El docente la califica. '
  'Luego la alumna rinde el examen: el servidor controla el tiempo y nunca le manda cuál es la respuesta correcta. Al enviar, se califica automáticamente.',
  'Web de la alumna: curso → Tareas → Entregar; docente → Calificar; alumna → Exámenes → Rendir.', 'Y la familia, ¿qué ve?'),
 ('4', 'Proceso 4 · Apoderada', 'Hermes', '1:00',
  'La apoderada ve a su hija: el resumen, la asistencia con la falta que acabamos de marcar, las notas del bimestre cerrado, el boletín en PDF y las cuotas, que puede pagar. '
  'Todo viene de cuatro servicios distintos, y cada familia solo ve lo suyo.',
  'Móvil o web de la apoderada: Inicio → Asistencia → Notas → Pagos.', 'Por último, el control del colegio.'),
 ('5', 'Proceso 5 · Administración y finanzas', 'Witmer', '2:00',
  'Finanzas muestra lo cobrado, la deuda vencida y los morosos. En Cobranza se ve cada pago; anular uno no lo borra: revierte el saldo y publica el evento. '
  'En Cuentas de acceso, el administrador restaura la cuenta de un alumno que olvidó la clave: vuelve a ser su documento y el sistema le obliga a cambiarla.',
  'Finanzas → Resumen / Morosos / Cobranza; Administración → Cuentas de acceso → Restaurar.', 'Cierre: ver la frase final.'),
]

PREGUNTAS = [
 ('¿Qué es el outbox y por qué?', 'Hermes', 'Guardar el dato y su evento en la misma transacción; un relé publica después. No se pierde ni se inventa un aviso.'),
 ('¿Cómo evitan vender la misma vacante dos veces?', 'Andre', 'Una sola sentencia: UPDATE … WHERE ocupados < capacidad. Es atómica y devuelve cero filas si no hay cupo.'),
 ('¿Qué pasa si el pago llega dos veces?', 'Andre / Witmer', 'Idempotencia: la clave del pago, la tabla eventos_procesados y el estado de la matrícula. No se cobra ni se procesa dos veces.'),
 ('¿Por qué la matrícula espera una aprobación después del pago?', 'Andre', 'Porque pagar no es lo mismo que ser admitido: el administrador revisa antes de matricular. Mientras tanto el cupo queda reservado y no vence.'),
 ('¿Por qué no usan una transacción global (2PC)?', 'Todos', 'Cada servicio tiene su base y queremos bajo acoplamiento. Usamos saga coreografiada con compensaciones.'),
 ('¿Cómo se anula un cobro?', 'Witmer', 'Con otra transacción: revierte saldos, anula el comprobante y publica pago.anulado. No se borra nada.'),
 ('¿Cómo saben quién es el usuario en cada servicio?', 'Hermes', 'Por el claim usuarioId del token; los endpoints internos lo traducen a docente, alumno o apoderado.'),
 ('¿Qué pasa si Kafka se cae?', 'Todos', 'Los eventos esperan en outbox_eventos y el relé reintenta cuando vuelve.'),
 ('¿Y si un servicio se cae?', 'Todos', 'Circuit breaker y respuestas controladas; la web muestra la franja de aviso y permite reintentar o volver a ingresar.'),
 ('¿Cómo prueban todo esto?', 'Todos', 'Pruebas con PostgreSQL real, seis escenarios de punta a punta, scripts de demostración y auditoría de pantallas.'),
 ('¿Qué falta?', 'Todos', 'Pasarela y correo reales, producción con pruebas de carga, prorrateo de pensiones y reembolso automático al rechazar una matrícula.'),
]

CSS = """
@page { size: A4; margin: 16mm 15mm 16mm 15mm; }
* { box-sizing: border-box; }
body { font-family: 'Liberation Sans', 'Noto Sans', Arial, sans-serif; color: #14213d; font-size: 10.6pt; line-height: 1.42; margin: 0; }
h1 { font-size: 24pt; color: #1F3A87; margin: 0 0 4px; }
h2 { font-size: 15pt; color: #1F3A87; margin: 0 0 8px; border-bottom: 2px solid #E8890C; padding-bottom: 4px; }
.sub { color: #4B5875; margin: 0 0 14px; }
.portada { page-break-after: always; }
table { border-collapse: collapse; width: 100%; margin: 8px 0 12px; }
th, td { border: 1px solid #d6dcec; padding: 5px 8px; text-align: left; vertical-align: top; }
th { background: #eef3ff; color: #1F3A87; }
.pill { display: inline-block; padding: 1px 9px; border-radius: 999px; color: #fff; font-weight: 700; font-size: 9pt; }
.card { border: 1px solid #d6dcec; border-left-width: 7px; border-radius: 8px; padding: 9px 12px 8px; margin: 0 0 10px; page-break-inside: avoid; background: #fff; }
.card .cab { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; margin-bottom: 3px; }
.card .titulo { font-weight: 700; font-size: 11.6pt; }
.card .meta { font-size: 9pt; color: #4B5875; white-space: nowrap; }
.card p { margin: 3px 0; }
.etq { font-size: 8.2pt; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: #4B5875; margin-top: 5px; }
.decir { font-size: 11pt; }
.mostrar { color: #0B5D41; }
.puente { color: #E8890C; font-style: italic; }
.nota { background: #fff7e8; border: 1px solid #f3d9a8; border-radius: 6px; padding: 7px 10px; margin: 10px 0; font-size: 9.6pt; }
.salto { page-break-before: always; }
ul { margin: 4px 0 8px 18px; padding: 0; }
"""

def card(n, titulo, quien, tiempo, decir, mostrar, puente):
    c = COLOR[quien]
    return (f'<div class="card" style="border-left-color:{c}"><div class="cab"><span class="titulo">{html.escape(str(n))} · {html.escape(titulo)}</span>'
            f'<span class="meta"><span class="pill" style="background:{c}">{quien}</span> &nbsp;{tiempo} min</span></div>'
            f'<div class="etq">Qué decir</div><p class="decir">{html.escape(decir)}</p>'
            f'<div class="etq">Qué mostrar</div><p class="mostrar">{html.escape(mostrar)}</p>'
            f'<div class="etq">Pase a la siguiente</div><p class="puente">{html.escape(puente)}</p></div>')

def resumen_por_persona():
    filas = []
    for q in ('Hermes', 'Andre', 'Witmer'):
        ns = [d[0] for d in DIAPOSITIVAS if d[2] == q]
        t = sum(int(d[3].split(':')[0]) * 60 + int(d[3].split(':')[1]) for d in DIAPOSITIVAS if d[2] == q)
        filas.append(f'<tr><td><span class="pill" style="background:{COLOR[q]}">{q}</span></td><td>{", ".join(map(str, ns))}</td><td>{t // 60} min {t % 60:02d} s</td></tr>')
    todos = [d[0] for d in DIAPOSITIVAS if d[2] == 'Todos']
    filas.append(f'<tr><td><span class="pill" style="background:{COLOR["Todos"]}">Todos</span></td><td>{", ".join(map(str, todos))}</td><td>(cada quien dice su parte)</td></tr>')
    return '<table><tr><th>Quién</th><th>Diapositivas</th><th>Tiempo propio</th></tr>' + ''.join(filas) + '</table>'

def construir():
    h = [f'<html><head><meta charset="utf-8"><style>{CSS}</style></head><body>']
    h.append('<div class="portada"><h1>EduSpace · Guion por diapositiva</h1>'
             '<p class="sub">Qué dice cada integrante, qué muestra y cómo pasa la palabra · Defensa DIST 2026-2 · ~45 minutos</p>'
             + resumen_por_persona() +
             '<h2>Cómo usar este guion</h2><ul>'
             '<li>La numeración sigue <b>docs/presentacion/04-diapositivas-para-generador.md</b>: la diapositiva N de este PDF es la N de la presentación.</li>'
             '<li><b>Qué decir</b> está escrito para decirse en voz alta, con tus palabras: no se lee de corrido, se usa como guía.</li>'
             '<li><b>Qué mostrar</b> indica qué señalar, abrir o ejecutar. <b>Pase</b> es la frase que le da la palabra al siguiente.</li>'
             '<li>Los tiempos son de referencia (suman ~40 min más ~5 de preguntas). Si falta tiempo, recorta la demostración, nunca la explicación del proceso.</li></ul>'
             '<div class="nota"><b>Reparto propuesto.</b> Hermes modera la apertura (1–7) y la seguridad (24); Witmer lleva las métricas (25); las diapositivas 23 y 26 las dicen los tres. '
             'Si prefieren otro reparto, solo cambien el nombre en cada tarjeta: el texto sirve para cualquiera.</div>'
             '<h2>Antes de empezar (15 minutos antes)</h2><ul>'
             '<li>Sistema levantado y 10 servicios en <b>UP</b> (Eureka). Web en <b>localhost:4200</b>; Mailpit en <b>localhost:18025</b>.</li>'
             '<li><code>python3 tools/demo/limpiar_pruebas.py</code> y luego <code>python3 tools/demo/preparar_exposicion.py</code>.</li>'
             '<li><code>python3 tools/demo/preparar_matricula_en_linea.py</code>: da los datos nuevos para la matrícula en línea (alumno, apoderado, correo) y libera cupos.</li>'
             '<li>Caja del cajero <b>abierta</b> (la usa la demostración de Witmer).</li>'
             '<li>Cada quien ensayó su script una vez hoy: <code>proceso_hermes.py</code>, <code>proceso_andre.py</code>, <code>proceso_witmer.py</code>.</li>'
             '<li>Capturas de respaldo guardadas por si falla la red.</li></ul></div>')
    tramos = [('Apertura · diapositivas 1–7 (6 min)', range(1, 8)), ('Bloque de Hermes · 8–12 (9 min)', range(8, 13)),
              ('Bloque de Andre · 13–17 (9 min)', range(13, 18)), ('Bloque de Witmer · 18–22 (9 min)', range(18, 23)),
              ('Cierre conjunto · 23–26 (5 min + preguntas)', range(23, 27))]
    for i, (nombre, rng) in enumerate(tramos):
        h.append(f'<h2 class="{"salto" if i else ""}">{nombre}</h2>')
        for d in DIAPOSITIVAS:
            if d[0] in rng:
                h.append(card(*d))
    h.append('<h2 class="salto">Versión corta · demostración de 10 minutos</h2>'
             '<p class="sub">Si el tiempo es corto, se presentan estas 5 escenas con la plataforma en vivo (guion completo en <b>docs/presentacion/05-demo-rapida.md</b>). '
             'Credenciales y comandos de preparación están ahí; todas las cuentas de la demo usan la clave <b>Expo12345</b>.</p>')
    for d in DEMO_RAPIDA:
        h.append(card('Escena ' + d[0], d[1], d[2], d[3], d[4], d[5], d[6]))
    h.append('<div class="nota"><b>Frase de cierre (15 s):</b> «Cinco procesos, tres roles móviles y una sola plataforma: ningún servicio lee la base de otro; todo pasa por transacciones locales y eventos, y si algo falla en medio, el sistema compensa.»</div>')
    h.append('<h2 class="salto">Hoja de respuestas · preguntas de defensa</h2><table><tr><th>Pregunta</th><th>Responde</th><th>Respuesta corta</th></tr>')
    for p, q, r in PREGUNTAS:
        h.append(f'<tr><td><b>{html.escape(p)}</b></td><td>{html.escape(q)}</td><td>{html.escape(r)}</td></tr>')
    h.append('</table><div class="nota"><b>Si algo falla en vivo:</b> «Quiero mostrarles justo cómo se comporta el sistema ante una falla» y pasen a la captura de respaldo. '
             'Si la web manda al inicio de sesión tras un reinicio, es el manejo de sesión: entren de nuevo y explíquenlo como una ventaja. Si aparece la franja «el sistema no responde», esperen un minuto y reintenten.</div>')
    h.append('</body></html>')
    return ''.join(h)

if __name__ == '__main__':
    with tempfile.TemporaryDirectory() as d:
        f = os.path.join(d, 'guion.html')
        open(f, 'w', encoding='utf-8').write(construir())
        r = subprocess.run(['google-chrome', '--headless=new', '--no-sandbox', '--disable-gpu', '--no-pdf-header-footer', f'--print-to-pdf={SALIDA}', 'file://' + f], capture_output=True, text=True)
    if not os.path.exists(SALIDA):
        sys.exit('No se generó el PDF: ' + r.stderr[-400:])
    print('PDF generado:', SALIDA)
