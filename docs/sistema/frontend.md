# Frontend

**Angular 22**, componentes standalone, *zoneless*, señales (`signal`/`computed`/`effect`), formularios reactivos, carga perezosa por ruta, pruebas con Vitest. Código en `frontend/`.

## Estructura
```
frontend/src/app/
├── core/        # servicios HTTP (18), modelos, interceptores (auth, X-Trace-ID), guards (auth, rol, modo), utilidades
├── layout/      # shell (menú, paleta Ctrl+K, barra de pestañas), menu.ts, hubs.ts
├── shared/      # modal, page-header, confirmación, notificaciones toast
└── features/    # 18 áreas: publico, auth, dashboard, alumnos, matriculas, planificacion, asistencia,
                 # calificaciones, pagos, finanzas, caja, notificaciones, docente, familia, calendario, ayuda, perfil, usuarios
```

## Menú del personal: 8 espacios de trabajo con pestañas
Las pantallas que se usan juntas se reúnen en un espacio (`layout/hubs.ts`); las URL no cambian y la barra de pestañas aparece sobre cualquier pantalla del espacio.

| Entrada del menú | Pestañas |
|---|---|
| Matrícula | Matrículas · Alumnos · Importar alumnos · Campañas · Cupos |
| Aulas y secciones | Panel de aulas · Secciones · Aulas físicas |
| Cursos y docentes | Panel de cursos · Cursos · Malla curricular · Docentes · Asignaciones |
| Horarios | Armado automático · Horario por sección · Aulas disponibles · Bloques horarios |
| Año escolar | Años y periodos · Feriados |
| Asistencia y notas | Justificaciones · Reporte de asistencia · Cierre de periodo · Libretas y boletines |
| Finanzas | Resumen · Cuentas por alumno · Cobranza · Morosos y alertas · Conceptos · Tarifas · Descuentos |
| Avisos y cuentas | Avisos y plantillas · Cuentas de acceso · Usuarios y roles |

Además: **Inicio** del personal (estadísticas, pre-matrículas, aulas con ficha, cursos y docentes), Notificaciones, Calendario, Ayuda, Mi perfil, y la **búsqueda Ctrl+K** (pantallas, pestañas y alumnos).

## Docentes, alumnos y apoderados: «Mis cursos» (sin menú lateral)
Estos tres roles no usan el menú lateral del personal: tienen una **barra superior** corta (Mis cursos · Horario · Notas/Asistencia/Exámenes · Pagos solo apoderado), búsqueda Ctrl+K, campana de notificaciones y un menú de cuenta (perfil, ayuda, cambiar clave, cerrar sesión). Quien tiene además un rol del personal o de caja conserva el menú lateral (`layout/aula-nav.ts`, `usaBarraAula`).

| Pantalla | Qué muestra |
|---|---|
| **Mis cursos** (alumno / apoderado) | Saludo, selector de hijo (apoderado), alerta de cuotas vencidas, **tarjetas de curso** (color e ícono propios, docente, promedio, tareas por entregar, próxima entrega), «Hoy» (clases del día) y «Próximas entregas» |
| **Un curso** (`/familia/curso/:cursoId`) | Cabecera con el color del curso y pestañas **Tareas** (con entrega de archivos para el alumno), **Notas** por periodo y **Exámenes** (rendir) |
| **Mis cursos** (docente) | Tarjetas de cada curso y sección con alumnos, aula, turno y tareas por calificar; «Hoy» y «Por calificar» |
| **Un curso** (`/docente/curso/:seccionId/:cursoId`) | Pestañas **Tareas** (crear, editar, publicar, cerrar y abrir las **entregas** para calificar o devolver sin salir de la página), **Asistencia** (hoja del día), **Notas** (criterios y registro), **Exámenes** (crear y revisar resultados) y **Alumnos** (con buscador); todo ya viene filtrado por ese curso y sección |

Los datos de cada curso vienen de la malla del grado, el horario de la sección y los servicios de tareas y notas; las pantallas clásicas (`/docente/tareas`, `/familia/tareas`, etc.) siguen existiendo por si se enlazan. Componentes compartidos: `shared/aula/` (tarjeta, cabecera y pestañas de curso, color/ícono por curso).

## Otros roles
| Rol | Menú |
|---|---|
| Cajero | Caja |
| Público | Inicio · Nosotros · Niveles · Admisión · Noticias · Contacto |

## Calidad
- 62 pruebas (`menu.spec.ts` verifica que cada ítem y cada pestaña apunta a una ruta que existe y que el rol puede abrir).
- Verificación en navegador real (Chrome headless con Playwright) para todos los roles, sin errores de consola ni respuestas 4xx/5xx.
- Accesibilidad: roles ARIA en pestañas, modales y paleta; foco visible; menú móvil (hamburguesa) y pestañas con desplazamiento horizontal.
- Carga inicial 384 kB sin comprimir / **105 kB** transferidos; 98 archivos JS (carga perezosa).

## Modos de autenticación
`npm start` (Keycloak, por defecto) o `npm run auth:eduspace` (login propio contra `auth-ms`); la ruta de usuarios solo existe en el modo propio.
