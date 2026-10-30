export type EstadoMatricula = 'PRE_MATRICULA' | 'POR_APROBAR' | 'MATRICULADO' | 'RETIRADO' | 'ANULADO' | 'TRASLADADO';
export type TipoMatricula = 'NUEVA' | 'RATIFICACION' | 'REINGRESO';
export type EstadoCampania = 'PLANIFICADA' | 'ABIERTA' | 'CERRADA';
export type TipoRequisito = 'FICHA_FIRMADA' | 'CONSTANCIA_NO_ADEUDO' | 'CERTIFICADO_ESTUDIOS' | 'EXPEDIENTE_COMPLETO';

export const ESTADOS_MATRICULA: EstadoMatricula[] = ['PRE_MATRICULA', 'POR_APROBAR', 'MATRICULADO', 'RETIRADO', 'TRASLADADO', 'ANULADO'];
export const TIPOS_MATRICULA: TipoMatricula[] = ['NUEVA', 'RATIFICACION', 'REINGRESO'];

export interface Matricula {
  id: number;
  codigo: string;
  alumnoId: number;
  alumnoNombre: string;
  anioId: number;
  nivelId: number;
  gradoId: number;
  seccionId: number;
  campaniaId?: number;
  tipo: TipoMatricula;
  estado: EstadoMatricula;
  matriculaAnteriorId?: number;
  venceEn?: string;
  observaciones?: string;
  creadoEn: string;
  /** Cuándo se registró el pago de la matrícula (queda POR_APROBAR hasta que la administración la apruebe). */
  pagadaEn?: string;
  aprobadaEn?: string;
  confirmadaEn?: string;
  cerradaEn?: string;
  motivoCierre?: string;
}

export interface MatriculaCreate {
  alumnoId: number;
  seccionId: number;
  tipo: TipoMatricula;
  observaciones?: string;
}

export interface Requisito {
  id: number;
  tipo: TipoRequisito;
  cumplido: boolean;
  observacion?: string;
  verificadoPor?: number;
  verificadoEn?: string;
}

export interface HistorialMatricula {
  id: number;
  tipoCambio: string;
  estadoAnterior?: string;
  estadoNuevo?: string;
  seccionAnteriorId?: number;
  seccionNuevaId?: number;
  motivo?: string;
  usuarioId?: number;
  fecha: string;
}

export interface MatriculaDetalle {
  matricula: Matricula;
  requisitos: Requisito[];
  historial: HistorialMatricula[];
}

export interface Campania {
  id: number;
  anioId: number;
  nombre: string;
  fechaInicio: string;
  fechaFin: string;
  estado: EstadoCampania;
}
export type CampaniaRequest = Omit<Campania, 'id' | 'estado'>;

export interface Cupo {
  seccionId: number;
  seccionNombre: string;
  anioId: number;
  gradoId: number;
  capacidad: number;
  ocupados: number;
  vacantes: number;
}

export interface RatificacionRequest {
  anioDestinoId: number;
  matriculaAnteriorId?: number;
  seccionOrigenId?: number;
  seccionDestinoId?: number;
  gradoDestinoId?: number;
}
export interface RatificacionResultado {
  solicitadas: number;
  creadas: number;
  matriculas: Matricula[];
  errores: { matriculaAnteriorId: number; alumnoId: number; motivo: string }[];
}

export interface FiltroMatriculas {
  anioId?: number;
  gradoId?: number;
  seccionId?: number;
  estado?: EstadoMatricula | '';
  texto?: string;
  page?: number;
  size?: number;
}

/** Fila de la lista de alumnos de una sección. */
export interface AlumnoSeccion {
  matriculaId: number;
  codigo: string;
  alumnoId: number;
  alumnoNombre: string;
  estado: EstadoMatricula;
}
