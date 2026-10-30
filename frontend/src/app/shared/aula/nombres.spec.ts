import { nombreNatural } from './nombres';
import { visualCurso } from './curso-visual';

describe('aula: nombres y aspecto de cursos', () => {
  it('pasa «Apellidos, Nombres» a «Nombres Apellidos»', () => {
    expect(nombreNatural('Paredes, Jorge')).toBe('Jorge Paredes');
    expect(nombreNatural('Ñahui Quispe, Valeria')).toBe('Valeria Ñahui Quispe');
    expect(nombreNatural('Sin coma')).toBe('Sin coma');
    expect(nombreNatural(null)).toBe('');
  });

  it('cada curso conserva su color y «Educación Física» no se confunde con «Física»', () => {
    expect(visualCurso('Matemática', 3).color).toBe(visualCurso('Matemática', 3).color);
    expect(visualCurso('Matemática', 3).color).not.toBe(visualCurso('Matemática', 4).color);
    expect(visualCurso('Educación Física', 1).emoji).toBe('⚽');
    expect(visualCurso('Ciencia y Tecnología', 1).emoji).toBe('🔬');
    expect(visualCurso('Algo desconocido', 1).emoji).toBe('📚');
  });
});
