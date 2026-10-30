# App móvil: instalación y funcionamiento

Código en `mobile/eduspace_movil/` (Flutter 3.44, Dart 3). Una sola base de código para Android y web (la versión web se usa para probar sin emulador).

## Qué se descarga y cuándo

Al abrir, la app solo trae el ingreso. **El código y los datos de cada rol se bajan cuando esa cuenta inicia sesión**: un docente descarga su módulo, la familia (alumno y apoderado) el suyo, y la pantalla «Preparando tu espacio…» aparece mientras tanto. En la versión web son archivos aparte (`*.part.js`); en Android van en el mismo paquete pero se cargan por separado. Al cerrar sesión se borra todo lo guardado de la cuenta (solo queda la dirección del servidor), así que otra persona en el mismo teléfono empieza desde cero. Detalle de cada pantalla en [Análisis por rol](00-analisis-por-rol.md).

## Estructura

```
lib/
  main.dart                 Puerta: ingreso → cambio de clave obligatorio → app según rol
  core/
    api.dart                Cliente HTTP: Bearer + X-Trace-ID, renueva token, distingue 401 de «servicio caído»
    sesion.dart             Tokens en el dispositivo, roles del JWT, cierre con motivo
    notificaciones.dart     Contador y bandeja (consulta cada 45 s)
    config.dart             URL del Gateway (editable desde el ingreso)
    formato.dart            Fechas y nombres en español
  ui/                       Tema, Cargador (carga + reintentar + deslizar para recargar), tarjetas
  pages/
    acceso.dart             Ingreso y cambio de clave
    comun.dart              Marco con barra inferior, notificaciones, perfil
    docente/                Hoy, cursos, horario, pasar lista, tareas (crear/publicar/calificar), materiales (subir archivo o enlace)
    familia/                Alumno y apoderado: inicio, tareas (entregar), horario, notas, asistencia, pagos, materiales
```

## Cómo se conecta

Todo pasa por el Gateway (`:18080`), igual que la web. La app usa las mismas rutas `/api/v1/...` y los mismos permisos: un docente solo ve lo suyo y una familia solo ve a sus hijos porque lo decide el backend, no la app.

| Dónde corre | URL del servidor |
|---|---|
| Navegador del mismo equipo | `http://localhost:18080` |
| Emulador de Android | `http://10.0.2.2:18080` (valor por defecto en Android) |
| Celular real en la misma red | `http://<IP del equipo>:18080` (se cambia en el ingreso → «Servidor») |

### Conectar un celular o emulador a la API local

La app **no lleva ninguna dirección fija**: se escribe en el ingreso (*Servidor* → *Probar conexión*). Con el sistema levantado en tu equipo:

```bash
tools/movil/red-local.sh          # muestra la IP y comprueba que el Gateway responde en ella
```

* Celular y equipo en la **misma red** (la IP es la de `red-local.sh`, p. ej. `http://192.168.1.25:18080`).
* Si usas otra red (por ejemplo el celular con datos o el equipo del salón), solo cambia la dirección en la app; no hay que recompilar.
* Cortafuegos (Fedora): `sudo firewall-cmd --add-port=18080/tcp --add-port=19000/tcp` (Gateway y MinIO).
* **Subir tareas desde el celular**: levanta con `EXPONER_HOST=<IP> tools/e2e/levantar.sh --auth-propio --sin-reset`; así las URL firmadas de MinIO apuntan a la IP y no a `localhost`.
* Mailpit y el resto de herramientas siguen siendo solo del equipo.

El Android de desarrollo permite tráfico `http` (`usesCleartextTraffic`). **En producción hay que servir el Gateway con HTTPS y quitar ese permiso.**

## Probarla

```bash
cd mobile/eduspace_movil
flutter pub get
flutter analyze && flutter test        # 5 pruebas: formato, login, 401→sesión vencida, 503→servicio caído, mensajes de error

# En el navegador (sin emulador)
flutter run -d chrome --web-port 5000  # el origen http://localhost:5000 ya está permitido en el CORS de DEV
# En un emulador o celular conectado
flutter run
```

El sistema debe estar arriba (`tools/e2e/levantar.sh --auth-propio`). Cuentas: la clave inicial es el documento de la persona; la app pide cambiarla.

### Compilar el APK

```bash
flutter doctor --android-licenses      # una vez, lo debe aceptar la persona que usa el equipo
flutter build apk --release
```

La licencia del SDK de Android requiere aceptación humana, por eso no se compiló el APK en este entorno; sí se verificó la app completa en su versión web contra el sistema real.

## Notificaciones push reales

Hoy la app consulta el contador de no leídas cada 45 s con la app abierta. Para recibir avisos con la app cerrada:

1. Crear un proyecto en Firebase y descargar `google-services.json` (Android) a `android/app/`.
2. Añadir `firebase_core` y `firebase_messaging` y registrar el token con `POST /api/v1/notificaciones/dispositivos` al iniciar sesión.
3. En `notificaciones-ms`, enviar la notificación por FCM además de guardarla en la bandeja.

Los pasos 1 y 3 necesitan credenciales de Google que no forman parte del repositorio.

## Qué se verificó

Con el sistema real (login propio) y la app web en un viewport de teléfono (390×844): ingreso y cambio de clave obligatorio; docente (hoy, cursos, crear y publicar una tarea, ver entregas, pasar lista); alumno (inicio, tareas, horario, notas con boletín); apoderado (pagos, notificaciones). Se comprobó el ciclo completo: la tarea creada por el docente llegó como notificación a la familia. Hallazgos corregidos durante la prueba: el listado de entregas es por alumno con la entrega anidada; `/alumnos/mios` identifica al alumno con `alumnoId`; `/asignaciones?anioId=` ignora `docenteId` (la app filtra); las cuotas pagadas ya no muestran «días de atraso».

## Limitaciones

* Sin push real (ver arriba) y sin modo sin conexión.
* Adjuntos de una tarea del docente y rendir exámenes: solo en la web (los **materiales del curso** sí se suben desde el móvil).
* La descarga de boletines abre la URL del archivo en el navegador del teléfono; si el almacenamiento (MinIO) entrega URLs con `localhost`, en un celular real habrá que configurar su dirección pública.
