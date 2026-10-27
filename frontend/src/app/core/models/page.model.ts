/** Página normalizada: auth-ms y pago-ms devuelven `totalElementos`, planificacion-ms devuelve `total`. */
export interface Page<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface ApiError {
  codigo?: number | string;
  mensaje?: string;
  /** calificaciones-ms devuelve el formato por defecto de Spring: `message` en vez de `mensaje`. */
  message?: string;
  ruta?: string;
  traceId?: string;
}
