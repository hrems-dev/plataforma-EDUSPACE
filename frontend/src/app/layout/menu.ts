export interface ItemMenu {
  etiqueta: string;
  ruta: string;
  icono: string;
  roles: string[];
  /** Solo existe con el login propio; con Keycloak las cuentas se administran en su consola. */
  soloEduspace?: boolean;
  /** Muestra el contador de notificaciones sin leer. */
  conContador?: boolean;
}

export interface GrupoMenu {
  titulo: string;
  items: ItemMenu[];
}

export const PERSONAL = ['ADMIN', 'SECRETARIA'];
export const DOCENTE = ['DOCENTE'];
export const FAMILIA = ['APODERADO', 'ALUMNO'];
export const TODOS = ['ADMIN', 'SECRETARIA', 'DOCENTE', 'APODERADO', 'ALUMNO', 'CAJERO'];

// Iconos: trazos de 24x24 (estilo lineal). Se agrupan por área; cada grupo solo se muestra si algún ítem es de un rol de la sesión.
export const MENU: GrupoMenu[] = [
  {
    titulo: 'General',
    items: [
      { etiqueta: 'Inicio', ruta: '/inicio', icono: 'M3 11l9-8 9 8M5 10v10h14V10', roles: PERSONAL },
      { etiqueta: 'Notificaciones', ruta: '/notificaciones', icono: 'M6 9a6 6 0 0112 0c0 6 2 7 2 7H4s2-1 2-7M10 20a2 2 0 004 0', roles: TODOS, conContador: true },
      { etiqueta: 'Calendario', ruta: '/calendario', icono: 'M4 5h16v15H4zM4 10h16M9 3v4M15 3v4', roles: TODOS },
      { etiqueta: 'Ayuda', ruta: '/ayuda', icono: 'M12 17h.01M9.5 9a2.5 2.5 0 115 0c0 1.5-2.5 2-2.5 4M21 12a9 9 0 11-18 0 9 9 0 0118 0', roles: TODOS },
      { etiqueta: 'Mi perfil', ruta: '/mi-perfil', icono: 'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z', roles: TODOS }
    ]
  },
  {
    titulo: 'Mi trabajo',
    items: [
      { etiqueta: 'Inicio', ruta: '/docente/inicio', icono: 'M3 11l9-8 9 8M5 10v10h14V10', roles: DOCENTE },
      { etiqueta: 'Mis alumnos', ruta: '/docente/alumnos', icono: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8', roles: DOCENTE },
      { etiqueta: 'Mi horario', ruta: '/docente/horario', icono: 'M4 5h16v15H4zM4 10h16M10 5v15', roles: DOCENTE },
      { etiqueta: 'Asistencia', ruta: '/docente/asistencia', icono: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0', roles: DOCENTE },
      { etiqueta: 'Tareas', ruta: '/docente/tareas', icono: 'M9 11l3 3 8-8M20 12v7a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2h9', roles: DOCENTE },
      { etiqueta: 'Exámenes', ruta: '/docente/examenes', icono: 'M5 4h14v16H5zM9 9h6M9 13h6M9 17h3', roles: DOCENTE },
      { etiqueta: 'Banco de preguntas', ruta: '/docente/banco', icono: 'M4 6h16M4 12h16M4 18h10M18 16v4M16 18h4', roles: DOCENTE },
      { etiqueta: 'Notas', ruta: '/docente/notas', icono: 'M3 20h18M6 20V10M12 20V4M18 20v-7', roles: DOCENTE }
    ]
  },
  {
    titulo: 'Mi familia',
    items: [
      { etiqueta: 'Inicio', ruta: '/familia/inicio', icono: 'M3 11l9-8 9 8M5 10v10h14V10', roles: FAMILIA },
      { etiqueta: 'Asistencia', ruta: '/familia/asistencia', icono: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0', roles: FAMILIA },
      { etiqueta: 'Notas', ruta: '/familia/notas', icono: 'M3 20h18M6 20V10M12 20V4M18 20v-7', roles: FAMILIA },
      { etiqueta: 'Boletines', ruta: '/familia/boletines', icono: 'M5 4h14v16H5zM9 9h6M9 13h6M9 17h3', roles: FAMILIA },
      { etiqueta: 'Tareas', ruta: '/familia/tareas', icono: 'M9 11l3 3 8-8M20 12v7a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2h9', roles: FAMILIA },
      { etiqueta: 'Exámenes', ruta: '/familia/examenes', icono: 'M4 5h16v14H4zM8 9h8M8 13h5', roles: FAMILIA },
      { etiqueta: 'Horario', ruta: '/familia/horario', icono: 'M4 5h16v15H4zM4 10h16M10 5v15', roles: FAMILIA },
      { etiqueta: 'Pagos', ruta: '/familia/pagos', icono: 'M3 7h18v10H3zM3 11h18', roles: ['APODERADO'] }
    ]
  },
  {
    titulo: 'Ventanilla',
    items: [{ etiqueta: 'Caja', ruta: '/caja', icono: 'M3 7h18v10H3zM3 11h18M7 15h2M12 15h5', roles: ['CAJERO'] }]
  },
  {
    titulo: 'Matrícula',
    items: [
      { etiqueta: 'Alumnos', ruta: '/alumnos', icono: 'M16 11a4 4 0 10-8 0M4 21a8 8 0 0116 0M19 8v6M22 11h-6', roles: PERSONAL },
      { etiqueta: 'Importar alumnos', ruta: '/alumnos/importar', icono: 'M12 3v12M7 10l5 5 5-5M4 20h16', roles: PERSONAL },
      { etiqueta: 'Matrículas', ruta: '/matriculas', icono: 'M4 5h16v14H4zM8 9h8M8 13h5', roles: PERSONAL },
      { etiqueta: 'Campañas', ruta: '/matriculas/campanias', icono: 'M4 6h16M4 12h10M4 18h6M17 14l3 3 3-3', roles: PERSONAL },
      { etiqueta: 'Cupos', ruta: '/matriculas/cupos', icono: 'M3 20h18M6 20V10M12 20V4M18 20v-7', roles: PERSONAL }
    ]
  },
  {
    titulo: 'Planificación',
    items: [
      { etiqueta: 'Años y periodos', ruta: '/planificacion/anios', icono: 'M4 5h16v15H4zM4 10h16M9 3v4M15 3v4', roles: PERSONAL },
      { etiqueta: 'Secciones', ruta: '/planificacion/secciones', icono: 'M4 6h16M4 12h16M4 18h10', roles: PERSONAL },
      { etiqueta: 'Cursos', ruta: '/planificacion/cursos', icono: 'M5 4h14v16H5zM9 9h6M9 13h6', roles: PERSONAL },
      { etiqueta: 'Docentes', ruta: '/planificacion/docentes', icono: 'M16 11a4 4 0 10-8 0M4 21a8 8 0 0116 0', roles: PERSONAL },
      { etiqueta: 'Aulas', ruta: '/planificacion/aulas', icono: 'M3 21V8l9-5 9 5v13M9 21v-6h6v6', roles: PERSONAL },
      { etiqueta: 'Malla curricular', ruta: '/planificacion/malla', icono: 'M4 6h16M4 12h16M4 18h16M8 3v18M16 3v18', roles: PERSONAL },
      { etiqueta: 'Asignaciones', ruta: '/planificacion/asignaciones', icono: 'M9 11l3 3 8-8M20 12v7a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2h9', roles: PERSONAL },
      { etiqueta: 'Bloques horarios', ruta: '/planificacion/bloques', icono: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0', roles: PERSONAL },
      { etiqueta: 'Horarios', ruta: '/planificacion/horarios', icono: 'M4 5h16v15H4zM4 10h16M10 5v15', roles: PERSONAL },
      { etiqueta: 'Feriados', ruta: '/planificacion/feriados', icono: 'M5 3v18M5 4h12l-2 4 2 4H5', roles: PERSONAL }
    ]
  },
  {
    titulo: 'Académico',
    items: [
      { etiqueta: 'Justificaciones', ruta: '/asistencia/justificaciones', icono: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0', roles: PERSONAL },
      { etiqueta: 'Reporte de asistencia', ruta: '/asistencia/reporte', icono: 'M3 20h18M6 20V10M12 20V4M18 20v-7', roles: PERSONAL },
      { etiqueta: 'Cierre de periodo', ruta: '/calificaciones/cierre', icono: 'M5 11V8a7 7 0 0114 0v3M5 11h14v10H5z', roles: PERSONAL },
      { etiqueta: 'Libretas y boletines', ruta: '/calificaciones/boletines', icono: 'M5 4h14v16H5zM9 9h6M9 13h6M9 17h3', roles: PERSONAL }
    ]
  },
  {
    titulo: 'Cobranza',
    items: [
      { etiqueta: 'Finanzas', ruta: '/finanzas', icono: 'M3 20h18M6 20v-6M11 20V8M16 20v-9M21 20V4', roles: PERSONAL },
      { etiqueta: 'Cuentas por alumno', ruta: '/pagos/cuentas', icono: 'M3 7h18v10H3zM3 11h18M7 15h2', roles: PERSONAL },
      { etiqueta: 'Cobranza', ruta: '/pagos/cobranza', icono: 'M4 19V9M10 19V5M16 19v-7M22 19H2', roles: PERSONAL },
      { etiqueta: 'Conceptos', ruta: '/pagos/conceptos', icono: 'M3 7h18v10H3zM3 11h18', roles: ['ADMIN'] },
      { etiqueta: 'Tarifas', ruta: '/pagos/tarifas', icono: 'M12 3v18M7 8h7a3 3 0 010 6H8a3 3 0 000 6h9', roles: ['ADMIN'] },
      { etiqueta: 'Descuentos', ruta: '/pagos/descuentos', icono: 'M19 5L5 19M7 7h.01M17 17h.01', roles: ['ADMIN'] },
      { etiqueta: 'Morosos y alertas', ruta: '/pagos/morosos', icono: 'M12 9v4M12 17h.01M10.3 3.9L2.4 18a2 2 0 001.7 3h15.8a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z', roles: PERSONAL }
    ]
  },
  {
    titulo: 'Administración',
    items: [
      { etiqueta: 'Plantillas y avisos', ruta: '/notificaciones/envio', icono: 'M4 6h16v12H4zM4 7l8 6 8-6', roles: ['ADMIN'] },
      { etiqueta: 'Usuarios y roles', ruta: '/usuarios', icono: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z', roles: ['ADMIN'], soloEduspace: true }
    ]
  }
];
