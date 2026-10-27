/** Pantalla de inicio según los roles de la sesión (el primero que coincida, en este orden de prioridad). */
export function rutaInicial(roles: readonly string[]): string {
  if (roles.includes('ADMIN') || roles.includes('SECRETARIA')) return '/inicio';
  if (roles.includes('DOCENTE')) return '/docente/inicio';
  if (roles.includes('APODERADO') || roles.includes('ALUMNO')) return '/familia/inicio';
  if (roles.includes('CAJERO')) return '/caja';
  return '/notificaciones';
}
