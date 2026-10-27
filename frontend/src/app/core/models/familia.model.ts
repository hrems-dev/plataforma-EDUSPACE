import { Parentesco } from './alumno.model';

/** Un hijo del apoderado (o el propio alumno) tal como lo devuelve alumnos-ms en `/alumnos/mios`. */
export interface Hijo {
  alumnoId: number;
  codigo: string;
  nombreCompleto: string;
  estado: string;
  parentesco?: Parentesco | null;
}
