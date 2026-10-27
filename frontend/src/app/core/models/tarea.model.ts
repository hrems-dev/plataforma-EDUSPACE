export type EstadoTarea = 'BORRADOR' | 'PUBLICADA' | 'CERRADA';
export type EstadoEntrega = 'ENTREGADA' | 'CALIFICADA' | 'DEVUELTA';

export interface Adjunto {
  id: number;
  nombreArchivo: string;
  contentType: string;
  tamanoBytes: number;
}

export interface Tarea {
  id: number;
  anioId: number;
  periodoId: number;
  seccionId: number;
  cursoId: number;
  docenteId: number;
  titulo: string;
  descripcion?: string;
  publicadaEn?: string;
  fechaEntrega: string;
  permiteTardia: boolean;
  puntajeMaximo: number;
  estado: EstadoTarea;
  adjuntos?: Adjunto[];
  entregadas?: number;
  calificadas?: number;
  pendientes?: number;
}

export interface TareaCreate {
  periodoId: number;
  seccionId: number;
  cursoId: number;
  titulo: string;
  descripcion?: string;
  fechaEntrega: string;
  permiteTardia?: boolean;
  puntajeMaximo?: number;
}
export type TareaUpdate = Partial<Pick<TareaCreate, 'titulo' | 'descripcion' | 'fechaEntrega' | 'permiteTardia' | 'puntajeMaximo'>>;

export interface ArchivoEntrega {
  id: number;
  nombreArchivo: string;
  contentType: string;
  tamanoBytes: number;
}

export interface Entrega {
  id: number;
  tareaId: number;
  matriculaId: number;
  alumnoId: number;
  alumnoNombre: string;
  comentario?: string;
  entregadaEn: string;
  esTardia: boolean;
  version: number;
  estado: EstadoEntrega;
  nota?: number;
  retroalimentacion?: string;
  calificadaEn?: string;
  archivos: ArchivoEntrega[];
}

export interface EntregaAlumno {
  matriculaId: number;
  alumnoId: number;
  alumnoNombre: string;
  /** SIN_ENTREGAR cuando el alumno aún no entregó. */
  estado: string;
  entrega?: Entrega;
}

export interface ArchivoRef {
  nombreArchivo: string;
  ruta: string;
  contentType: string;
  tamanoBytes: number;
}

export interface TareaPendiente {
  tareaId: number;
  titulo: string;
  cursoId: number;
  fechaEntrega: string;
  vencida: boolean;
  permiteTardia: boolean;
  alumnoId: number;
  alumnoNombre: string;
  estadoEntrega: string;
}

export interface UrlSubida {
  uploadUrl: string;
  ruta: string;
  expiraMinutos: number;
}
