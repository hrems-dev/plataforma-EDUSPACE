/** Aspecto de un curso (color e ícono) para que cada uno se reconozca de un vistazo en las tarjetas y cabeceras. */
export interface CursoVisual {
  /** Tono principal (HSL) y su versión suave para fondos. */
  color: string;
  suave: string;
  emoji: string;
}

const ICONOS: [RegExp, string][] = [
  [/matem|aritm|geom|algebra|trigon/i, '🧮'],
  [/comunic|lengu|lectur|literat|redacc/i, '📖'],
  [/ciencia|biolog|qu[ií]mic|f[ií]sic|natural/i, '🔬'],
  [/arte|dibujo|m[uú]sic|cultura/i, '🎨'],
  [/f[ií]sica|deport|educaci[oó]n f/i, '⚽'],
  [/ingl[eé]s|idioma|english/i, '🌎'],
  [/religi/i, '🕊️'],
  [/social|histor|geograf|ciudad|c[ií]vic/i, '🌍'],
  [/tutor/i, '💬'],
  [/comput|inform|tecnolog|rob[oó]t/i, '💻'],
  [/emprend|trabajo|econom/i, '💡']
];

/** Un mismo curso siempre recibe el mismo color (depende de su id), aunque se escriba distinto en otra pantalla. */
export function visualCurso(nombre: string, id: number): CursoVisual {
  // «Educación Física» debe ganar a «Física»: se busca primero lo más específico.
  const emoji = (/educaci[oó]n f/i.test(nombre) ? '⚽' : ICONOS.find(([re]) => re.test(nombre))?.[1]) ?? '📚';
  const tono = (Math.abs(id) * 47 + 215) % 360;
  return { color: `hsl(${tono} 62% 42%)`, suave: `hsl(${tono} 80% 95%)`, emoji };
}
