/** Rutas del sitio público (no piden sesión). */
const PUBLICAS = ['/', '/nosotros', '/niveles', '/admision', '/noticias', '/contacto'];

export function esRutaPublica(ruta: string): boolean {
  const limpia = ruta.split(/[?#]/)[0].replace(/\/+$/, '') || '/';
  return PUBLICAS.includes(limpia);
}
