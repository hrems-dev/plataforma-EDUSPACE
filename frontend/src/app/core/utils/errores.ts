import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/page.model';

/** Mensaje legible para mostrar al usuario a partir de un error HTTP del backend. */
export function mensajeError(err: unknown, porDefecto = 'No se pudo completar la operación.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'No hay conexión con el servidor.';
    }
    if (err.status === 429) {
      return 'Demasiadas peticiones. Espera unos segundos e intenta de nuevo.';
    }
    const cuerpo = err.error as ApiError | null;
    if (cuerpo && typeof cuerpo === 'object') {
      const texto = cuerpo.mensaje ?? cuerpo.message;
      if (texto) {
        return texto;
      }
    }
  }
  return porDefecto;
}

export function nombreCompleto(p: { nombres?: string; apellidos?: string }): string {
  return `${p.apellidos ?? ''}, ${p.nombres ?? ''}`.replace(/^, |, $/g, '');
}
