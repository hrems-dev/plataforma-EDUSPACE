import { Route } from '@angular/router';
import { routes } from '../app.routes';
import { rutaInicial } from '../core/utils/landing';
import { NAV_AULA, usaBarraAula } from './aula-nav';
import { HUBS, hubDe } from './hubs';
import { MENU } from './menu';

/** Rutas hijas del shell con su regla de roles (`data.roles`); sin regla = cualquier usuario con sesión. */
function rutasDelShell(): Map<string, string[] | undefined> {
  // El shell del portal es el que exige sesión (el otro grupo con hijos es el sitio público).
  const shell = routes.find(r => r.children && r.canActivate) as Route;
  const mapa = new Map<string, string[] | undefined>();
  for (const r of shell.children ?? []) {
    if (r.path) mapa.set('/' + r.path, r.data?.['roles'] as string[] | undefined);
  }
  return mapa;
}

const COMUNES = ['/notificaciones', '/calendario', '/ayuda', '/mi-perfil'];
const ROLES = ['ADMIN', 'SECRETARIA', 'DOCENTE', 'APODERADO', 'ALUMNO', 'CAJERO'];
const itemsDe = (rol: string) => MENU.flatMap(g => g.items).filter(i => i.roles.includes(rol));

describe('menú y rutas', () => {
  it('todo ítem del menú apunta a una ruta que existe', () => {
    const rutas = rutasDelShell();
    const muertos = MENU.flatMap(g => g.items).filter(i => !rutas.has(i.ruta)).map(i => i.ruta);
    expect(muertos).toEqual([]);
  });

  it('un rol solo ve ítems cuya ruta también lo deja entrar (el menú nunca muestra un enlace que el guard rechaza)', () => {
    const rutas = rutasDelShell();
    const incoherentes: string[] = [];
    for (const item of MENU.flatMap(g => g.items)) {
      const permitidos = rutas.get(item.ruta);
      if (!permitidos) continue; // ruta sin restricción de rol
      for (const rol of item.roles) {
        if (!permitidos.includes(rol)) incoherentes.push(`${rol} ve "${item.etiqueta}" pero ${item.ruta} no lo admite`);
      }
    }
    expect(incoherentes).toEqual([]);
  });

  it('el docente no ve nada de administración, ni la familia lo del personal', () => {
    const rutasDocente = itemsDe('DOCENTE').map(i => i.ruta);
    expect(rutasDocente.some(r => r.startsWith('/usuarios') || r.startsWith('/pagos') || r.startsWith('/matriculas') || r.startsWith('/alumnos'))).toBe(false);
    const rutasFamilia = itemsDe('ALUMNO').map(i => i.ruta);
    expect(rutasFamilia.every(r => r.startsWith('/familia') || COMUNES.includes(r))).toBe(true);
    expect(itemsDe('ALUMNO').some(i => i.ruta === '/familia/pagos')).toBe(false); // pagar es del apoderado
    expect(itemsDe('APODERADO').some(i => i.ruta === '/familia/pagos')).toBe(true);
  });

  it('cada rol tiene su pantalla de inicio y esa pantalla es una ruta permitida para él', () => {
    const rutas = rutasDelShell();
    for (const rol of ROLES) {
      const inicio = rutaInicial([rol]);
      const permitidos = rutas.get(inicio);
      expect(rutas.has(inicio) || inicio === '/notificaciones', `${rol} → ${inicio}`).toBe(true);
      if (permitidos) expect(permitidos.includes(rol), `${rol} no puede entrar a su inicio ${inicio}`).toBe(true);
    }
  });

  it('las pantallas comunes (calendario, ayuda, perfil) las ve todo rol y no piden rol en la ruta', () => {
    const rutas = rutasDelShell();
    for (const r of COMUNES) {
      expect(rutas.has(r), r).toBe(true);
      expect(rutas.get(r), `${r} no debe restringir por rol`).toBeUndefined();
      for (const rol of ROLES) expect(itemsDe(rol).some(i => i.ruta === r), `${rol} debería ver ${r}`).toBe(true);
    }
  });

  it('el sitio público no exige sesión y el portal sí', () => {
    const publico = routes.find(r => r.children && !r.canActivate) as Route;
    const hijas = (publico.children ?? []).map(r => '/' + r.path);
    for (const p of ['/', '/nosotros', '/niveles', '/admision', '/noticias', '/contacto']) expect(hijas, p).toContain(p === '/' ? '/' : p);
  });

  it('el ADMIN gana prioridad cuando tiene varios roles', () => {
    expect(rutaInicial(['DOCENTE', 'ADMIN'])).toBe('/inicio');
    expect(rutaInicial(['APODERADO', 'DOCENTE'])).toBe('/docente/inicio');
  });
});

describe('espacios de trabajo (hubs)', () => {
  const rutas = rutasDelShell();

  it('toda pestaña apunta a una ruta que existe y que su rol puede abrir', () => {
    const problemas: string[] = [];
    for (const h of HUBS) {
      for (const p of h.pestanas) {
        if (!rutas.has(p.ruta)) problemas.push(`${h.id}: ${p.ruta} no existe`);
        const permitidos = rutas.get(p.ruta);
        if (permitidos) for (const r of p.roles) if (!permitidos.includes(r)) problemas.push(`${h.id}: ${r} ve «${p.etiqueta}» pero la ruta no lo admite`);
      }
    }
    expect(problemas).toEqual([]);
  });

  it('ninguna ruta está en dos espacios y la primera pestaña la ve todo rol del hub', () => {
    const vistas = HUBS.flatMap(h => h.pestanas.map(p => p.ruta));
    expect(vistas.filter((r, i) => vistas.indexOf(r) !== i)).toEqual([]);
    for (const h of HUBS) {
      const entrada = MENU.flatMap(g => g.items).find(i => i.hub === h.id)!;
      expect(entrada, h.id).toBeDefined();
      for (const rol of entrada.roles) expect(h.pestanas[0].roles.includes(rol), `${rol} no puede abrir la primera pestaña de ${h.id}`).toBe(true);
    }
  });

  it('una URL se asigna a la pestaña más específica y los detalles caen en su pestaña', () => {
    expect(hubDe('/alumnos/importar')?.pestana.etiqueta).toBe('Importar alumnos');
    expect(hubDe('/alumnos/12')?.pestana.etiqueta).toBe('Alumnos');
    expect(hubDe('/matriculas?nueva=1')?.pestana.etiqueta).toBe('Matrículas');
    expect(hubDe('/matriculas/cupos')?.pestana.etiqueta).toBe('Cupos');
    expect(hubDe('/planificacion/aulas-disponibles')?.hub.id).toBe('horarios');
    expect(hubDe('/planificacion/aulas')?.hub.id).toBe('aulas');
    expect(hubDe('/inicio')).toBeNull();
  });

  it('el menú del personal queda corto', () => {
    expect(itemsDe('ADMIN').length).toBeLessThanOrEqual(15);
  });
});

describe('barra superior de docentes, alumnos y apoderados', () => {
  const rutas = rutasDelShell();

  it('cada enlace existe y su rol puede abrirlo', () => {
    const problemas: string[] = [];
    for (const e of NAV_AULA) {
      if (!rutas.has(e.ruta)) problemas.push(`${e.ruta} no existe`);
      const permitidos = rutas.get(e.ruta);
      if (permitidos) for (const r of e.roles) if (!permitidos.includes(r)) problemas.push(`${r} ve «${e.etiqueta}» pero ${e.ruta} no lo admite`);
    }
    expect(problemas).toEqual([]);
  });

  it('solo quien tiene únicamente roles de aula usa la barra; el personal y la caja mantienen el menú lateral', () => {
    expect(usaBarraAula(['DOCENTE'])).toBe(true);
    expect(usaBarraAula(['APODERADO', 'ALUMNO'])).toBe(true);
    expect(usaBarraAula(['DOCENTE', 'ADMIN'])).toBe(false);
    expect(usaBarraAula(['SECRETARIA'])).toBe(false);
    expect(usaBarraAula(['CAJERO'])).toBe(false);
    expect(usaBarraAula([])).toBe(false);
  });

  it('las páginas de un curso mantienen resaltado «Mis cursos»', () => {
    const mis = (rol: string) => NAV_AULA.find(e => e.roles.includes(rol) && e.etiqueta === 'Mis cursos')!;
    expect(mis('DOCENTE').prefijos).toContain('/docente/curso');
    expect(mis('ALUMNO').prefijos).toContain('/familia/curso');
  });
});
