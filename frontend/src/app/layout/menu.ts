import { HUBS } from './hubs';

export interface ItemMenu {
  etiqueta: string;
  ruta: string;
  icono: string;
  roles: string[];
  /** Solo existe con el login propio; con Keycloak las cuentas se administran en su consola. */
  soloEduspace?: boolean;
  /** Muestra el contador de notificaciones sin leer. */
  conContador?: boolean;
  /** Si es la entrada de un espacio de trabajo (hub): resalta mientras se esté en cualquiera de sus pestañas. */
  hub?: string;
}

export interface GrupoMenu {
  titulo: string;
  items: ItemMenu[];
}

export const PERSONAL = ['ADMIN', 'SECRETARIA'];
export const DOCENTE = ['DOCENTE'];
export const FAMILIA = ['APODERADO', 'ALUMNO'];
export const TODOS = ['ADMIN', 'SECRETARIA', 'DOCENTE', 'APODERADO', 'ALUMNO', 'CAJERO'];

/** Entrada del menú que abre un espacio de trabajo: va a su primera pestaña y se resalta con cualquiera de ellas. */
function entradaHub(id: string): ItemMenu {
  const hub = HUBS.find(h => h.id === id)!;
  const roles = [...new Set(hub.pestanas.flatMap(p => p.roles))];
  return {
    etiqueta: hub.titulo,
    ruta: hub.pestanas[0].ruta,
    icono: hub.icono,
    roles,
    hub: hub.id,
    soloEduspace: hub.pestanas.every(p => p.soloEduspace) || undefined
  };
}

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
    titulo: 'Gestión del colegio',
    items: [
      entradaHub('matricula'),
      entradaHub('aulas'),
      entradaHub('cursos'),
      entradaHub('horarios'),
      entradaHub('anio'),
      entradaHub('seguimiento'),
      entradaHub('finanzas')
    ]
  },
  {
    titulo: 'Administración',
    items: [entradaHub('administracion')]
  }
];
