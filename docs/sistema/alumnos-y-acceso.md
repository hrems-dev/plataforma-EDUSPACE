# Alumnos y entrada al sistema

## 1. El alumno en el sistema
`alumnos-ms` guarda el **expediente** de cada alumno; `matricula-ms` decide en qué sección está; el resto de servicios lo ven por eventos.

| Dato | Detalle |
|---|---|
| Identificación | Código `ALaaaa-NNNNN` (año + correlativo, generado bajo candado), documento (DNI u otro; **único**), nombres, apellido paterno y materno |
| Personales | Fecha de nacimiento, sexo, nacionalidad, dirección y distrito, teléfono, correo, colegio de procedencia |
| Estado | `ACTIVO`, `INACTIVO`, `RETIRADO`, `TRASLADADO`, `EGRESADO`, con **historial de cambios** y motivo |
| Apoderados | Varios por alumno con parentesco (padre, madre, abuelo/a, tío/a, hermano, tutor, otro); uno es **principal** y uno es **responsable de pago**; indican si vive con el alumno y si puede recogerlo |
| Documentos | DNI, partida de nacimiento, ficha SIAGIE (**requeridos y verificados** para matricular), certificado de estudios, carnet de vacunación, foto y otros; se guardan en MinIO con URL firmada |
| Salud | Ficha cerrada por defecto (datos sensibles de menores) |
| Cuenta de acceso | `usuarioId` que lo vincula con su usuario (ver §3) |

### Ciclo del alumno
```
registro (con apoderados y documentos) → expediente completo → pre-matrícula → pago de matrícula → MATRICULADO en una sección
   → asistencia · tareas · exámenes · notas · pensiones → boletín → ratificación del año siguiente / retiro / traslado
```

### Qué ve cada quien
| Quién | Qué puede hacer |
|---|---|
| ADMIN / SECRETARIA | Registrar, importar por CSV, editar, cambiar estado, gestionar apoderados y documentos, ver el expediente |
| DOCENTE | Ver los alumnos de sus secciones, pasar lista, calificar |
| ALUMNO | Su inicio, horario, asistencia, notas, boletines, tareas y exámenes |
| APODERADO | Lo mismo que el alumno para **cada uno de sus hijos**, justificar inasistencias y pagar pensiones |

Pantallas: *Matrícula → Alumnos* (lista, expediente, documentos, apoderados, salud, historial), *Importar alumnos* (CSV) y el *Panel de aulas* (alumnos por sección, matricular y trasladar).

### Matrícula por el sitio web
Además de la secretaría, la familia puede matricular desde el sitio público: se crean el alumno y el apoderado, se paga la matrícula y **las cuentas se crean solas**
(usuario `nombre.apellido`, clave inicial = documento) con la constancia por correo. Ver [Matrícula en línea](matricula-en-linea.md).

## 2. Cómo se entra al sistema

| Paso | Qué ocurre |
|---|---|
| 1 | La persona abre el portal (`/login`) o el sitio público del colegio y elige **Ingresar** |
| 2 | Escribe su **usuario** y su **clave** |
| 3 | El sistema valida la cuenta: 5 intentos fallidos la bloquean 15 minutos; una cuenta desactivada no entra |
| 4 | Si la clave es la inicial, el portal la lleva a **Cambiar clave** y no la deja ir a ninguna otra pantalla hasta cambiarla |
| 5 | Entra a **su pantalla de inicio según su rol** (personal → *Inicio*; docente → *Mi inicio*; familia → *Inicio de familia*; caja → *Caja*) |
| 6 | Cada petición lleva su token; a los 30 min se renueva con el refresco; *Cerrar sesión* lo revoca |

Hay dos modos de identidad (ver [Seguridad](seguridad.md)):
- **Cuentas del colegio** (`auth-ms`, login propio): el que usa PROD y el que permite crear y restaurar cuentas desde el portal (§3).
- **Keycloak** (DEV por defecto): la identidad vive en Keycloak (PKCE); las cuentas se administran en su consola. En este modo la pantalla de cuentas del colegio no se muestra.

Cambiar de modo: `tools/e2e/levantar.sh --auth-propio` + `npm run auth:eduspace` (cuentas del colegio) o `levantar.sh` + `npm run auth:keycloak` (Keycloak).

## 3. Cuentas de alumnos, docentes y demás personal

### Regla de creación
| | |
|---|---|
| **Usuario** | primer nombre + `.` + primer apellido, en minúsculas y sin tildes: *María José Aguilar Ortiz* → `maria.aguilar`. Si ya existe se agrega un número (`ana.gomez2`) |
| **Clave inicial** | el **documento de identidad** (DNI) |
| **Primer ingreso** | el sistema obliga a cambiar la clave antes de seguir (`debe_cambiar_clave`); la nueva debe tener al menos 8 caracteres, letras y números y ser distinta de la inicial |
| **Rol** | `ALUMNO`, `DOCENTE`, `APODERADO` (y `SECRETARIA`/`CAJERO` por el alta manual). **Nunca `ADMIN`** desde este flujo |
| **Vínculo** | la cuenta queda enlazada a la ficha de la persona (`usuarioId`), que es como los servicios saben quién es quién |
| **Correo** | si la persona no tiene, se usa `usuario@cuentas.eduspace.pe` (solo identifica; no recibe correo) |

### Dónde se administra (ADMIN) — *Avisos y cuentas → Cuentas de acceso*
- Pestañas **Alumnos**, **Docentes**, **Apoderados** y **Secretaría y caja**, con búsqueda y filtro por estado.
- Estados: *Sin cuenta*, *Aún no cambia su clave*, *Activa*, *Bloqueada*, *Desactivada*; con último ingreso.
- **Crear cuenta** (una) o **Crear N cuentas faltantes** (todas las que no tienen).
- **Restaurar** (cuando alguien olvidó su clave): la clave **vuelve a ser su documento** y debe cambiarla al entrar; además se desbloquea la cuenta y se cierran sus sesiones.
- **Desbloquear**, **Desactivar/Activar**.
- Secretaría y caja no tienen documento en su ficha: **Nueva clave** genera una clave temporal que se muestra una sola vez.
- También en la ficha de cada alumno (*Matrícula → Alumnos → alumno*): ver su usuario y *Crear cuenta* / *Restaurar clave*.
- **Las cuentas de administración no se crean ni se restauran desde aquí** (el servicio lo rechaza).

### Quién cambia su propia clave
Cualquier persona, en **Mi perfil → Cambiar mi clave** (o en la pantalla obligatoria del primer ingreso). La pantalla de ingreso explica cómo entrar la primera vez.

### API (auth-ms, solo ADMIN)
| Endpoint | Qué hace |
|---|---|
| `POST /api/v1/auth/usuarios/cuentas` | Crea la cuenta de una persona (nombres, apellidos, documento, rol) |
| `POST /api/v1/auth/usuarios/{id}/restablecer-por-documento` | Restaura la cuenta con el documento como clave (rechaza ADMIN) |
| `POST /api/v1/auth/usuarios/{id}/restablecer-clave` | Genera una clave temporal aleatoria (se muestra una vez) |
| `PATCH /api/v1/auth/usuarios/{id}/desbloquear\|activar\|desactivar` | Estado de la cuenta |
| `PATCH /api/v1/alumnos/{id}/usuario` · `/apoderados/{id}/usuario` · `/api/v1/planificacion/docentes/{id}/usuario` | Vincula la cuenta a la ficha |

### Consideraciones de seguridad
- La clave inicial (el DNI) **no es secreta**: por eso el cambio es obligatorio y las sesiones y refrescos de la cuenta se invalidan al restaurar.
- Quien restaura debe confirmar que la persona es quien dice ser (en secretaría, con documento).
- Los datos de los menores se muestran solo a su familia y a su personal autorizado; el apoderado solo ve a sus hijos.
- Recomendado para PROD: avisar a las familias por un canal oficial, y no reutilizar el DNI como clave después del primer cambio.
