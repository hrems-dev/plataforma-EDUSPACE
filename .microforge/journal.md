
## 2026-10-27 04:13 — Mejora de tarjeta Mis Cursos e integración de API en panel Docente

Se mejoró la tarjeta de «Mis cursos» en el panel de inicio del docente (docente-inicio.component) para reemplazar la lista estática de insignias por un grid de tarjetas interactivas. Ahora consume la API de matricula-ms (/api/v1/matriculas/cupos) y planificacion-ms (/api/v1/planificacion/secciones) para mostrar el número de alumnos matriculados, aula asignada, turno y accesos directos a Notas, Asistencia, Tareas y Exámenes.

## 2026-10-27 04:20 — Rediseño visual de tarjetas KPI en Dashboard

Se rediseñó el componente de indicadores (section.kpis) en el Dashboard (dashboard.component.html) y en la hoja de estilos global (styles.css). Se estructuraron las tarjetas KPI con: cabecera con etiquetas descriptivas en mayúsculas pequeñas, iconos SVG temáticos con fondos suaves (primario, violeta, éxito, peligro, advertencia), valores numéricos grandes con colores condicionales según estado, divisores sutiles, microinteracciones con elevación en hover (transición y sombra suave) y enlaces con flechas de acción directa.
