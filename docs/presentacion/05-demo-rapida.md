# Demostración rápida (10 minutos): cinco procesos

Recorrido corto para mostrar **la mayor parte del sistema** sin perderse. Cada proceso dura 2 minutos, se cuenta de la misma forma (*quién · qué hace · qué pasa por dentro*) y termina en una pantalla donde **se ve el resultado**.

## Preparación (10 minutos antes, una sola vez)

```bash
tools/e2e/levantar.sh --auth-propio --sin-reset      # o sin --sin-reset si es la primera vez
python3 tools/demo/limpiar_pruebas.py                # (si hubo ensayos) retira matrículas de prueba y libera cupos
python3 tools/demo/preparar_exposicion.py            # cuentas, examen, tarea, material y notas listos (se puede repetir)
python3 tools/demo/preparar_matricula_en_linea.py    # datos NUEVOS (alumno, apoderado, correo) para hacer la matrícula en línea otra vez; libera cupos
tools/movil/red-local.sh                             # dirección para el celular (ver abajo)
cd frontend && npm start                             # web en http://localhost:4200  (config en modo «eduspace»)
```

El script deja la **hoja de credenciales** (clave de las cuentas de la demo: `Expo12345`):

| Rol | Usuario | Para qué |
|---|---|---|
| Administración | `admin` / `admin123` | **Aprobar matrículas**, finanzas |
| Secretaría | `secretaria` / `secre123` | Expediente y matrícula presencial |
| Docente | `jorge.paredes` | Pasar lista, tareas, exámenes (Matemática, 4.° A) |
| Alumna | `lucia.mendoza` | Entregar tarea y rendir el examen |
| Apoderada | `carmen.rios` | Ver clases, notas y asistencia de su hija |

Abre **cuatro ventanas** ya con sesión iniciada (admin, docente, alumna y apoderada en navegadores o perfiles distintos) y Mailpit (`http://localhost:18025`). Así no se pierde tiempo escribiendo claves.

### Celular conectado al equipo (opcional, muy vistoso)

1. Celular y equipo en la **misma red**. Ejecuta `tools/movil/red-local.sh`: te dice la IP y la dirección para la app (`http://<IP>:18080`).
2. Si el cortafuegos está activo: `sudo firewall-cmd --add-port=18080/tcp --add-port=19000/tcp`.
3. Para subir tareas desde el celular, levanta con `EXPONER_HOST=<IP> tools/e2e/levantar.sh --auth-propio --sin-reset` (las URL firmadas de MinIO deben apuntar a la IP, no a `localhost`).
4. En la app: pantalla de ingreso → **Servidor** → escribe la dirección → **Probar conexión** → Guardar.

## Los cinco procesos

### 1 · Nuevo alumno: matrícula en línea y aprobación administrativa (3 min)

*Hilo: «una familia nueva entra al colegio».*

| # | Dónde | Acción | Qué mostrar / decir |
|---|---|---|---|
| 1 | Sitio público `/matricula-en-linea` (para otra familia: botón **Hacer otra matrícula**) | La familia llena los datos del alumno y del apoderado, elige grado y sección | Se **reserva la vacante** (cupo atómico). Sin cuenta, con honeypot anti-bots |
| 2 | Misma página | Paga con la tarjeta de prueba `4242 4242 4242 4242` | Pasarela simulada; `…0002` es rechazada: se muestra el rechazo y que se puede reintentar |
| 3 | Misma página | Termina en «**Pago recibido, tu matrícula está en revisión**» | Aún **no hay usuario ni clave**; el cupo queda reservado |
| 4 | Mailpit `:18025` | Abrir el primer correo | «Pago recibido · en revisión» |
| 5 | **Matrícula → Por aprobar** (sesión `admin`) | *Revisar* → ver origen, pago y expediente → **Aprobar matrícula** | **Solo el administrador** aprueba (secretaría recibe 403). Aquí también se puede **Rechazar** con motivo |
| 6 | Mailpit | Abrir el segundo correo | «**Matrícula aprobada**»: recuadro **Para iniciar sesión** (usuario y clave inicial), constancia PDF, horario y pensiones |
| 7 | Web/móvil | Iniciar sesión con el usuario del correo | Clave inicial = documento → el sistema **obliga a cambiarla** |
| 8 | **Finanzas → Cuentas por alumno** | Buscar al alumno | Matrícula pagada y las 10 pensiones **recién generadas** |

La **aprobación administrativa** es una compuerta explícita: el pago deja la matrícula `POR_APROBAR` (reserva el cupo pero no matricula) y solo el ADMIN la pasa a `MATRICULADO`. Eso publica `matricula.confirmada`, que genera las pensiones y habilita al alumno en asistencia y tareas. Si la rechaza, se anula, se libera el cupo y se avisa a la familia; el pago se devuelve desde Finanzas.

*Por dentro (30 s):* saga coreografiada con una **aprobación humana en medio** — `matricula.creada` → pago → `POR_APROBAR` → (admin) → `matricula.confirmada` → cuentas, cuotas y correo. Idempotente: repetir el pago o el clic de aprobar no duplica nada.

### 2 · Profesor en clase: horario, lista y tarea (2 min)

*Hilo: «el docente llega a su aula».*

| # | Dónde | Acción | Qué mostrar |
|---|---|---|---|
| 1 | **Móvil** (docente) → *Hoy* | Mostrar la clase del día | Horario publicado por planificación |
| 2 | Tocar la clase → **Pasar lista** | Marcar a un alumno con falta, el resto presente; *Guardar y cerrar* | Registro bloqueado al cerrar |
| 3 | Móvil → *Cursos → Matemática → Materiales → Subir material* | Subir un PDF (o un enlace) del curso | Los alumnos y apoderados **ven el material** en su app y reciben un aviso (`material.publicado`) |
| 4 | Móvil → *Tareas → Nueva tarea* | Crear y publicar | «Publicar avisa a las familias» |
| 5 | Móvil de la alumna / web apoderada | Aparece la **notificación** | Evento `tarea.publicada` → notificaciones-ms |

### 3 · Alumna: entrega la tarea y da el examen (2 min)

*Hilo: «la alumna responde».*

| # | Dónde | Acción | Qué mostrar |
|---|---|---|---|
| 1 | Móvil (alumna) → *Tareas* | Entregar la «Tarea de demostración» con una foto/PDF | El archivo sube **directo al almacenamiento** con URL firmada |
| 2 | Web docente → *Tareas* | Ver la entrega y **calificar** | Nota + retroalimentación |
| 3 | Web alumna → *Exámenes* | Rendir el «Examen de demostración» (10 min, 3 preguntas) | La respuesta correcta **nunca viaja** al navegador del alumno; temporizador del servidor |
| 4 | Al enviar | Ver el resultado | Calificación automática; el evento `examen.calificado` alimenta las notas |

### 4 · Apoderada: sigue a su hija (1 min)

*Hilo: «la familia ve el resultado».*

| # | Dónde | Acción | Qué mostrar |
|---|---|---|---|
| 1 | Móvil/web (apoderada) → *Inicio* | Resumen: pendientes, % de asistencia, clases de hoy | Todo viene de 4 servicios distintos |
| 2 | *Asistencia* | La falta que marcó el docente hace un momento | Tiempo real entre servicios |
| 3 | *Notas* | Promedio del **bimestre cerrado** (Matemática) y el boletín en PDF | Calificaciones agrega lo que mandan tareas y exámenes; **la familia solo ve periodos cerrados** (el script de preparación cierra el bimestre de 4.° A) |
| 4 | *Más → Pagos* | Cuotas pendientes; **Pagar** una (simulado) | Idempotencia: doble toque, un solo cobro |

### 5 · Administración y finanzas: control (2 min)

*Hilo: «el colegio ve todo».*

| # | Dónde | Acción | Qué mostrar |
|---|---|---|---|
| 1 | **Finanzas → Resumen / Morosos y alertas** | Recaudación, deuda, morosos | Cobranza con recordatorios |
| 2 | **Finanzas → Cobranza** (secretaría/caja) | Cobrar una cuota presencial | Comprobante; **anular** el pago revierte la cuota (compensación) |
| 3 | **Administración → Cuentas de acceso** | Restaurar la cuenta de una alumna | La clave vuelve al documento y se **obliga a cambiarla** |
| 4 | Grafana / `/actuator/health` | Estado y métricas | Observabilidad: `X-Trace-ID` de punta a punta |

## Ensayo verificado (qué se comprobó que funciona)
Se recorrieron, con el sistema real, estos flujos **de punta a punta en el navegador y en la app móvil**: matrícula en línea → pago → «en revisión» → aprobación del admin → correos (pago recibido y aprobada) → ingreso con la cuenta nueva y cambio obligatorio de clave; docente (pasar lista, materiales, tareas, calificar); alumna (entregar tarea con archivo, rendir el examen y ver su nota); apoderada (inicio, asistencia, notas, horario, pagos, boletines); administración (31 pantallas sin errores, restaurar cuenta, cobranza y finanzas). Además corren sin fallos `proceso_hermes.py`, `proceso_andre.py` y `proceso_witmer.py`.

## Si algo falla en vivo

| Síntoma | Qué hacer |
|---|---|
| La web manda al login con «sistema reiniciado» | Es el manejo de sesión: iniciar de nuevo y explicarlo como una **ventaja** (se verá en la pregunta de defensa) |
| Banner «el sistema no responde» | Esperar ~1 min (Eureka tarda en registrar un servicio que se reinició) y *Reintentar* |
| El examen de la demo ya se rindió | `python3 tools/demo/preparar_exposicion.py` crea uno nuevo |
| El celular no conecta | `tools/movil/red-local.sh`, abrir el puerto y «Probar conexión» en la app |
| No llega el correo | Mailpit, no el Gmail real: `http://localhost:18025` |

## Frase de cierre (15 s)

> «Cinco procesos, tres roles móviles y una sola plataforma: ningún servicio lee la base de otro; todo pasa por transacciones locales y eventos, y si algo falla en medio, el sistema compensa.»
