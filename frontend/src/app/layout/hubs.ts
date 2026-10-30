/**
 * Espacios de trabajo del personal: cada uno reúne en pestañas las pantallas que se usan juntas
 * (p. ej. todo lo de matrícula, o todo lo de horarios), para no saltar por el menú entre ellas.
 * Las rutas no cambian: la barra de pestañas se muestra sobre cualquier pantalla que figure aquí.
 */
export interface Pestana {
  etiqueta: string;
  ruta: string;
  roles: string[];
  /** Solo existe con el login propio (con Keycloak las cuentas se administran en su consola). */
  soloEduspace?: boolean;
}

export interface Hub {
  id: string;
  titulo: string;
  descripcion: string;
  icono: string;
  pestanas: Pestana[];
}

const PERSONAL = ['ADMIN', 'SECRETARIA'];
const ADMIN = ['ADMIN'];

export const HUBS: Hub[] = [
  {
    id: 'matricula',
    titulo: 'Matrícula',
    descripcion: 'Alumnos, matrículas, campañas y cupos',
    icono: 'M4 5h16v14H4zM8 9h8M8 13h5',
    pestanas: [
      { etiqueta: 'Matrículas', ruta: '/matriculas', roles: PERSONAL },
      { etiqueta: 'Por aprobar', ruta: '/matriculas/aprobaciones', roles: ADMIN },
      { etiqueta: 'Alumnos', ruta: '/alumnos', roles: PERSONAL },
      { etiqueta: 'Importar alumnos', ruta: '/alumnos/importar', roles: PERSONAL },
      { etiqueta: 'Campañas', ruta: '/matriculas/campanias', roles: PERSONAL },
      { etiqueta: 'Cupos', ruta: '/matriculas/cupos', roles: PERSONAL }
    ]
  },
  {
    id: 'aulas',
    titulo: 'Aulas y secciones',
    descripcion: 'Secciones, tutores, alumnos y aulas físicas',
    icono: 'M3 21V8l9-5 9 5v13M9 21v-6h6v6',
    pestanas: [
      { etiqueta: 'Panel de aulas', ruta: '/planificacion/panel', roles: PERSONAL },
      { etiqueta: 'Secciones', ruta: '/planificacion/secciones', roles: PERSONAL },
      { etiqueta: 'Aulas físicas', ruta: '/planificacion/aulas', roles: PERSONAL }
    ]
  },
  {
    id: 'cursos',
    titulo: 'Cursos y docentes',
    descripcion: 'Cursos, malla, docentes y quién dicta qué',
    icono: 'M5 4h14v16H5zM9 9h6M9 13h6',
    pestanas: [
      { etiqueta: 'Panel de cursos', ruta: '/planificacion/cursos-docentes', roles: PERSONAL },
      { etiqueta: 'Cursos', ruta: '/planificacion/cursos', roles: PERSONAL },
      { etiqueta: 'Malla curricular', ruta: '/planificacion/malla', roles: PERSONAL },
      { etiqueta: 'Docentes', ruta: '/planificacion/docentes', roles: PERSONAL },
      { etiqueta: 'Asignaciones', ruta: '/planificacion/asignaciones', roles: PERSONAL }
    ]
  },
  {
    id: 'horarios',
    titulo: 'Horarios',
    descripcion: 'Armado automático, edición, aulas libres y bloques',
    icono: 'M4 5h16v15H4zM4 10h16M10 5v15',
    pestanas: [
      { etiqueta: 'Armado automático', ruta: '/planificacion/horarios-auto', roles: PERSONAL },
      { etiqueta: 'Horario por sección', ruta: '/planificacion/horarios', roles: PERSONAL },
      { etiqueta: 'Aulas disponibles', ruta: '/planificacion/aulas-disponibles', roles: PERSONAL },
      { etiqueta: 'Bloques horarios', ruta: '/planificacion/bloques', roles: PERSONAL }
    ]
  },
  {
    id: 'anio',
    titulo: 'Año escolar',
    descripcion: 'Años, periodos y feriados',
    icono: 'M4 5h16v15H4zM4 10h16M9 3v4M15 3v4',
    pestanas: [
      { etiqueta: 'Años y periodos', ruta: '/planificacion/anios', roles: PERSONAL },
      { etiqueta: 'Feriados', ruta: '/planificacion/feriados', roles: PERSONAL }
    ]
  },
  {
    id: 'seguimiento',
    titulo: 'Asistencia y notas',
    descripcion: 'Justificaciones, reportes, cierre de periodo y libretas',
    icono: 'M3 20h18M6 20V10M12 20V4M18 20v-7',
    pestanas: [
      { etiqueta: 'Justificaciones', ruta: '/asistencia/justificaciones', roles: PERSONAL },
      { etiqueta: 'Reporte de asistencia', ruta: '/asistencia/reporte', roles: PERSONAL },
      { etiqueta: 'Cierre de periodo', ruta: '/calificaciones/cierre', roles: PERSONAL },
      { etiqueta: 'Libretas y boletines', ruta: '/calificaciones/boletines', roles: PERSONAL }
    ]
  },
  {
    id: 'finanzas',
    titulo: 'Finanzas',
    descripcion: 'Resumen, cuentas, cobranza, morosos y tarifas',
    icono: 'M3 20h18M6 20v-6M11 20V8M16 20v-9M21 20V4',
    pestanas: [
      { etiqueta: 'Resumen', ruta: '/finanzas', roles: PERSONAL },
      { etiqueta: 'Cuentas por alumno', ruta: '/pagos/cuentas', roles: PERSONAL },
      { etiqueta: 'Cobranza', ruta: '/pagos/cobranza', roles: PERSONAL },
      { etiqueta: 'Morosos y alertas', ruta: '/pagos/morosos', roles: PERSONAL },
      { etiqueta: 'Conceptos', ruta: '/pagos/conceptos', roles: ADMIN },
      { etiqueta: 'Tarifas', ruta: '/pagos/tarifas', roles: ADMIN },
      { etiqueta: 'Descuentos', ruta: '/pagos/descuentos', roles: ADMIN }
    ]
  },
  {
    id: 'administracion',
    titulo: 'Avisos y cuentas',
    descripcion: 'Avisos a las familias, plantillas, cuentas de acceso y usuarios',
    icono: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z',
    pestanas: [
      { etiqueta: 'Avisos y plantillas', ruta: '/notificaciones/envio', roles: ADMIN },
      { etiqueta: 'Cuentas de acceso', ruta: '/cuentas', roles: ADMIN, soloEduspace: true },
      { etiqueta: 'Usuarios y roles', ruta: '/usuarios', roles: ADMIN, soloEduspace: true }
    ]
  }
];

/** Quita parámetros y fragmento de una URL. */
export const sinParametros = (url: string): string => url.split(/[?#]/)[0];

const coincide = (url: string, ruta: string): boolean => url === ruta || url.startsWith(ruta + '/');

/** El hub al que pertenece una URL y su pestaña (gana la ruta más específica: `/alumnos/importar` antes que `/alumnos`). */
export function hubDe(url: string): { hub: Hub; pestana: Pestana } | null {
  const u = sinParametros(url);
  let mejor: { hub: Hub; pestana: Pestana } | null = null;
  for (const hub of HUBS) {
    for (const pestana of hub.pestanas) {
      if (coincide(u, pestana.ruta) && (!mejor || pestana.ruta.length > mejor.pestana.ruta.length)) mejor = { hub, pestana };
    }
  }
  return mejor;
}
