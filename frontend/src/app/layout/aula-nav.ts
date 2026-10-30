/**
 * Navegación de las personas que usan el sistema en el día a día (docentes, alumnos y apoderados):
 * una barra superior corta en lugar del menú lateral; los cursos llevan a sus tareas, notas y exámenes.
 * El personal administrativo y la caja mantienen el menú lateral.
 */
export interface EnlaceAula {
  etiqueta: string;
  ruta: string;
  icono: string;
  roles: string[];
  /** Rutas que mantienen resaltado el enlace (por ejemplo la página de un curso resalta «Mis cursos»). */
  prefijos: string[];
}

export const ROLES_AULA = ['DOCENTE', 'APODERADO', 'ALUMNO'];
const DOCENTE = ['DOCENTE'];
const FAMILIA = ['APODERADO', 'ALUMNO'];

export const NAV_AULA: EnlaceAula[] = [
  { etiqueta: 'Mis cursos', ruta: '/docente/inicio', icono: 'M3 11l9-8 9 8M5 10v10h14V10', roles: DOCENTE, prefijos: ['/docente/inicio', '/docente/curso', '/docente/tareas', '/docente/alumnos'] },
  { etiqueta: 'Horario', ruta: '/docente/horario', icono: 'M4 5h16v15H4zM4 10h16M10 5v15', roles: DOCENTE, prefijos: ['/docente/horario'] },
  { etiqueta: 'Asistencia', ruta: '/docente/asistencia', icono: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0', roles: DOCENTE, prefijos: ['/docente/asistencia'] },
  { etiqueta: 'Exámenes', ruta: '/docente/examenes', icono: 'M5 4h14v16H5zM9 9h6M9 13h6M9 17h3', roles: DOCENTE, prefijos: ['/docente/examenes', '/docente/banco'] },
  { etiqueta: 'Notas', ruta: '/docente/notas', icono: 'M3 20h18M6 20V10M12 20V4M18 20v-7', roles: DOCENTE, prefijos: ['/docente/notas'] },

  { etiqueta: 'Mis cursos', ruta: '/familia/inicio', icono: 'M3 11l9-8 9 8M5 10v10h14V10', roles: FAMILIA, prefijos: ['/familia/inicio', '/familia/curso', '/familia/tareas', '/familia/examenes'] },
  { etiqueta: 'Horario', ruta: '/familia/horario', icono: 'M4 5h16v15H4zM4 10h16M10 5v15', roles: FAMILIA, prefijos: ['/familia/horario'] },
  { etiqueta: 'Notas', ruta: '/familia/notas', icono: 'M3 20h18M6 20V10M12 20V4M18 20v-7', roles: FAMILIA, prefijos: ['/familia/notas', '/familia/boletines'] },
  { etiqueta: 'Asistencia', ruta: '/familia/asistencia', icono: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0', roles: FAMILIA, prefijos: ['/familia/asistencia'] },
  { etiqueta: 'Pagos', ruta: '/familia/pagos', icono: 'M3 7h18v10H3zM3 11h18', roles: ['APODERADO'], prefijos: ['/familia/pagos'] }
];

/** Quien tiene solo roles de aula (ninguno del personal ni de caja) usa la barra superior. */
export function usaBarraAula(roles: readonly string[]): boolean {
  return roles.length > 0 && roles.every(r => ROLES_AULA.includes(r));
}
