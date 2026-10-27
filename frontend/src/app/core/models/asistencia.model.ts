export type EstadoAsistencia = 'PRESENTE' | 'FALTA' | 'TARDANZA' | 'JUSTIFICADO';
export type TipoSesion = 'DIA' | 'CURSO';
export type TipoJustificacion = 'MEDICA' | 'FAMILIAR' | 'OTRO';
export type EstadoJustificacion = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';

export const ESTADOS_ASISTENCIA: EstadoAsistencia[] = ['PRESENTE', 'TARDANZA', 'FALTA', 'JUSTIFICADO'];
export const TIPOS_JUSTIFICACION: TipoJustificacion[] = ['MEDICA', 'FAMILIAR', 'OTRO'];

export interface DetalleAsistencia {
  detalleId: number;
  matriculaId: number;
  alumnoId: number;
  alumnoNombre: string;
  estado: EstadoAsistencia;
  minutosTarde?: number;
  observacion?: string;
  confirmado: boolean;
}

export interface Sesion {
  id: number;
  anioId: number;
  periodoId: number;
  seccionId: number;
  fecha: string;
  tipo: TipoSesion;
  cursoId?: number;
  bloqueId?: number;
  docenteId?: number;
  estado: 'ABIERTA' | 'CERRADA';
  cerradaEn?: string;
  alumnos: DetalleAsistencia[];
}

export interface SesionCreate {
  seccionId: number;
  fecha: string;
  tipo: TipoSesion;
  cursoId?: number;
  bloqueId?: number;
  docenteId?: number;
}

export interface ItemRegistro {
  matriculaId: number;
  estado: EstadoAsistencia;
  minutosTarde?: number;
  observacion?: string;
}

export interface Justificacion {
  id: number;
  matriculaId: number;
  alumnoId: number;
  fechaDesde: string;
  fechaHasta: string;
  tipo: TipoJustificacion;
  motivo: string;
  rutaArchivo?: string;
  estado: EstadoJustificacion;
  solicitadoPor: number;
  resueltoPor?: number;
  resueltoEn?: string;
  comentarioResolucion?: string;
  creadoEn: string;
  faltasJustificadas?: number;
}

export interface JustificacionCreate {
  alumnoId: number;
  fechaDesde: string;
  fechaHasta: string;
  tipo: TipoJustificacion;
  motivo: string;
}

export interface ConteoAsistencia {
  sesiones: number;
  presentes: number;
  faltas: number;
  tardanzas: number;
  justificadas: number;
  porcentajeAsistencia: number;
}

export interface FilaReporte {
  alumnoId: number;
  matriculaId: number;
  alumnoNombre: string;
  dias: ConteoAsistencia;
  excesoFaltas: boolean;
}

export interface ReporteSeccion {
  seccionId: number;
  desde?: string;
  hasta?: string;
  totalAlumnos: number;
  alumnos: FilaReporte[];
  alertasExcesoFaltas: FilaReporte[];
}

export interface DetalleDia {
  fecha: string;
  tipo: TipoSesion;
  cursoId?: number;
  estado: EstadoAsistencia;
  minutosTarde?: number;
}

export interface ResumenAlumno {
  alumnoId: number;
  desde?: string;
  hasta?: string;
  periodoId?: number;
  dias: ConteoAsistencia;
  cursos: ConteoAsistencia;
}

export interface AsistenciaHijo {
  alumnoId: number;
  alumnoNombre: string;
  resumen: ResumenAlumno;
  ultimos: DetalleDia[];
}

export interface ArchivoUrl {
  uploadUrl: string;
  ruta: string;
  expiraMinutos: number;
}
