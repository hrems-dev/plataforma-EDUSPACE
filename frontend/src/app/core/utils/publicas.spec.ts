import { esRutaPublica } from './publicas';

describe('esRutaPublica', () => {
  it('reconoce las páginas del sitio del colegio, con o sin parámetros y barra final', () => {
    for (const r of ['/', '/nosotros', '/niveles?nivel=primaria', '/admision/', '/noticias#x', '/contacto']) expect(esRutaPublica(r), r).toBe(true);
  });

  it('el portal no es público', () => {
    for (const r of ['/inicio', '/portal', '/alumnos', '/login', '/familia/pagos']) expect(esRutaPublica(r), r).toBe(false);
  });
});
