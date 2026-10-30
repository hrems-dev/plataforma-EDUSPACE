import { partirNombre } from './cuentas.service';

describe('partirNombre', () => {
  it('separa «Apellidos, Nombres» como lo entrega alumnos-ms', () => {
    expect(partirNombre('Aguilar Ortiz, Mariana')).toEqual({ apellidos: 'Aguilar Ortiz', nombres: 'Mariana' });
    expect(partirNombre('Flores Medina Vargas, Valentina María')).toEqual({ apellidos: 'Flores Medina Vargas', nombres: 'Valentina María' });
  });

  it('sin coma devuelve el texto en ambos campos en vez de fallar', () => {
    expect(partirNombre('Mariana')).toEqual({ apellidos: 'Mariana', nombres: 'Mariana' });
  });
});
