/** «Apellidos, Nombres» (como lo entregan los servicios) → «Nombres Apellidos». Sin coma se deja igual. */
export function nombreNatural(completo: string | null | undefined): string {
  if (!completo) return '';
  const i = completo.indexOf(',');
  return i < 0 ? completo.trim() : `${completo.slice(i + 1).trim()} ${completo.slice(0, i).trim()}`;
}
