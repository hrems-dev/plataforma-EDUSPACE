
## 2026-10-27 04:13 — Mejora de tarjeta Mis Cursos e integración de API en panel Docente

Se mejoró la tarjeta de «Mis cursos» en el panel de inicio del docente (docente-inicio.component) para reemplazar la lista estática de insignias por un grid de tarjetas interactivas. Ahora consume la API de matricula-ms (/api/v1/matriculas/cupos) y planificacion-ms (/api/v1/planificacion/secciones) para mostrar el número de alumnos matriculados, aula asignada, turno y accesos directos a Notas, Asistencia, Tareas y Exámenes.

## 2026-10-27 04:20 — Rediseño visual de tarjetas KPI en Dashboard

Se rediseñó el componente de indicadores (section.kpis) en el Dashboard (dashboard.component.html) y en la hoja de estilos global (styles.css). Se estructuraron las tarjetas KPI con: cabecera con etiquetas descriptivas en mayúsculas pequeñas, iconos SVG temáticos con fondos suaves (primario, violeta, éxito, peligro, advertencia), valores numéricos grandes con colores condicionales según estado, divisores sutiles, microinteracciones con elevación en hover (transición y sombra suave) y enlaces con flechas de acción directa.

## 2026-10-28 02:33 — Auth con Keycloak por defecto en DEV

El frontend usaba authMode=keycloak pero gateway y los 9 servicios validaban el JWT con el JWKS de auth-ms (puerto 8090), por lo que rechazaban los tokens de Keycloak. Se cambió el valor por defecto de JWK_SET_URI en los 10 *-dev.yml de infra/eduspace-config/config-repo a http://localhost:18090/realms/eduspace/protocol/openid-connect/certs. Para volver a auth-ms: exportar JWK_SET_URI=http://localhost:8090/.well-known/jwks.json y usar npm run auth:eduspace en el frontend. Requiere reiniciar config-server, gateway y servicios.

## 2026-10-29 21:46 — Rediseño visual de identidad escolar y análisis integral del sistema

Se realizó el análisis arquitectónico completo de los 10 microservicios backend, servicios de infraestructura y todos los módulos del frontend. Se rediseñó integralmente el sitio público del Colegio Horizonte del Valle (layout, inicio, nosotros, niveles, admisión, noticias y contacto) y el portal EduSpace: se incorporó el escudo heráldico colegial, ilustraciones vectoriales SVG escolares para el campus, los 3 niveles educativos (Inicial, Primaria, Secundaria), las 8 noticias y las 8 instalaciones del campus, preservando rigurosamente toda la estructura, datos y lógica existentes.

## 2026-10-29 21:56 — Generación e integración de fotografías de alta resolución para el sitio público

Se generaron e integraron 8 fotografías profesionales de alta resolución para el Colegio Horizonte del Valle: vista panorámica del campus moderno, aula de educación inicial con juegos Montessori, aula colaborativa de primaria, laboratorio de ciencias de secundaria, taller de innovación y robótica, polideportivo techado con partido de básquetbol, biblioteca escolar luminosa y visita guiada familiar de admisión. Las imágenes enriquecen la portada, los niveles educativos, las noticias destacadas y el catálogo de instalaciones sin modificar los textos ni la estructura existente.

## 2026-10-29 22:01 — Fondo transparente en el banner principal institucional

Se modificó el estilo del banner principal de la portada (.pub-heroe) para que posea fondo transparente, permitiendo una transición suave y luminosa con la cabecera y el cuerpo del sitio. Los textos, etiquetas y el panel lateral de próximos eventos se ajustaron a tipografía azul marino institucional (#1e3a8a) y acentos dorados sobre fondo transparente.

## 2026-10-29 22:08 — Integración de imagen fotográfica del campus al fondo del banner héroe

Se configuró la fotografía de alta resolución del campus (campus-hero.jpg) como imagen de fondo de todo el banner principal institucional (.pub-heroe), combinada con una capa translúcida azul marino/pizarra para máxima legibilidad y estética escolar prestigiosa. El panel de próximos eventos flota sobre la imagen con efecto de vidrio esmerilado (glassmorphism).
