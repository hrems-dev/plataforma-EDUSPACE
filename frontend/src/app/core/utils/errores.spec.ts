import { HttpErrorResponse } from '@angular/common/http';
import { mensajeError, nombreCompleto } from './errores';

describe('mensajeError', () => {
  it('lee `mensaje` (formato de la mayoría de servicios)', () => {
    expect(mensajeError(new HttpErrorResponse({ status: 409, error: { codigo: 409, mensaje: 'El cupo está lleno' } }))).toBe('El cupo está lleno');
  });

  it('lee `message` (formato por defecto de Spring que usa calificaciones-ms)', () => {
    expect(mensajeError(new HttpErrorResponse({ status: 400, error: { status: 400, message: "Falta registrar nota en el curso 1" } }))).toContain('Falta registrar nota');
  });

  it('traduce la falta de conexión y el límite de peticiones', () => {
    expect(mensajeError(new HttpErrorResponse({ status: 0 }))).toContain('conexión');
    expect(mensajeError(new HttpErrorResponse({ status: 429 }))).toContain('Demasiadas peticiones');
  });

  it('usa el texto por defecto cuando el error no trae mensaje', () => {
    expect(mensajeError(new HttpErrorResponse({ status: 500, error: null }), 'Falló')).toBe('Falló');
    expect(mensajeError(new Error('x'), 'Falló')).toBe('Falló');
  });

  it('arma "Apellidos, Nombres" sin comas sobrantes', () => {
    expect(nombreCompleto({ nombres: 'Ana', apellidos: 'Gómez' })).toBe('Gómez, Ana');
    expect(nombreCompleto({ nombres: 'Ana' })).toBe('Ana');
  });
});
