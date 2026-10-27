export type OrigenCriterio = 'MANUAL' | 'TAREAS' | 'EXAMENES';
export type EstadoCierre = 'ABIERTO' | 'CERRADO' | 'REABIERTO';

export interface Criterio {
  id: number;
  anioId: number;
  periodoId: number;
  seccionId: number;
  cursoId: number;
  nombre: string;
  peso: number;
  origenTipo: OrigenCriterio;
  creadoPor?: number;
  creadoEn?: string;
}
export interface CriterioRequest {
  anioId: number;
  periodoId: number;
  seccionId: number;
  cursoId: number;
  nombre: string;
  peso: number;
  origenTipo: OrigenCriterio;
}

export interface ValidarPesos {
  periodoId: number;
  seccionId: number;
  cursoId: number;
  sumaPesos: number;
  esValido: boolean;
  mensaje: string;
  criterios: Criterio[];
}

export interface FilaRegistro {
  matriculaId: number;
  alumnoId: number;
  alumnoNombre: string;
  notasPorCriterioId: Record<string, number | null>;
  promedio?: number;
  literal?: string;
}
export interface RegistroNotas {
  seccionId: number;
  cursoId: number;
  periodoId: number;
  estadoPeriodo: string;
  criterios: Criterio[];
  alumnos: FilaRegistro[];
}

export interface NotaItem {
  matriculaId: number;
  alumnoId: number;
  valor: number;
}
export interface NotasLote {
  seccionId: number;
  cursoId: number;
  periodoId: number;
  criterioId: number;
  notas: NotaItem[];
}

export interface NotaPendiente {
  id: string;
  origen: 'TAREA' | 'EXAMEN';
  origenId: number;
  titulo: string;
  alumnoId: number;
  matriculaId: number;
  seccionId: number;
  cursoId: number;
  periodoId: number;
  valor: number;
  fechaEvento: string;
  motivo?: string;
}

export interface Promedio {
  id: number;
  matriculaId: number;
  alumnoId: number;
  alumnoNombre: string;
  seccionId: number;
  cursoId: number;
  periodoId: number;
  promedio: number;
  literal: string;
  estado: 'BORRADOR' | 'CERRADO';
  calculadoEn: string;
}

export interface CierrePeriodo {
  id: number;
  seccionId: number;
  periodoId: number;
  estado: EstadoCierre;
  cerradoPor?: number;
  cerradoEn?: string;
  reabiertoPor?: number;
  reabiertoEn?: string;
  motivoReapertura?: string;
}

export interface Boletin {
  id: number;
  matriculaId: number;
  alumnoId: number;
  periodoId: number;
  version: number;
  estado: 'VIGENTE' | 'ANULADO';
  rutaPdf?: string;
  urlDescarga?: string;
  generadoPor?: number;
  generadoEn: string;
}

export interface ResultadoAnual {
  id: number;
  matriculaId: number;
  alumnoId: number;
  anioId: number;
  cursoId: number;
  promedioFinal: number;
  literal: string;
  estado: 'APROBADO' | 'DESAPROBADO' | 'RECUPERACION';
  calculadoEn: string;
}

export interface PeriodoNota {
  periodoId: number;
  promedio: number;
  literal: string;
  estado: string;
}
export interface CursoLibreta {
  cursoId: number;
  cursoNombre: string;
  notasPorPeriodo: Record<string, PeriodoNota>;
  promedioFinal?: number;
  literalFinal?: string;
  estadoFinal?: string;
}
export interface Libreta {
  alumnoId: number;
  alumnoNombre: string;
  matriculaId: number;
  anioId: number;
  nivelId: number;
  gradoId: number;
  seccionId: number;
  cursos: CursoLibreta[];
  resultadosAnuales: ResultadoAnual[];
  boletines: Boletin[];
}

export interface GenerarBoletin {
  seccionId?: number;
  periodoId?: number;
  alumnoId?: number;
  anioId?: number;
}
