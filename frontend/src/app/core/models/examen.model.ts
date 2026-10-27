export type TipoPregunta = 'OPCION_UNICA' | 'OPCION_MULTIPLE' | 'VERDADERO_FALSO' | 'ABIERTA';
export type EstadoExamen = 'BORRADOR' | 'PUBLICADO' | 'CERRADO' | 'CALIFICADO';
export type EstadoIntento = 'EN_CURSO' | 'ENVIADO' | 'CALIFICADO' | 'VENCIDO';

export const TIPOS_PREGUNTA: TipoPregunta[] = ['OPCION_UNICA', 'OPCION_MULTIPLE', 'VERDADERO_FALSO', 'ABIERTA'];

export interface Opcion {
  id?: number;
  texto: string;
  esCorrecta?: boolean;
  orden?: number;
}

export interface Pregunta {
  id: number;
  cursoId: number;
  docenteId: number;
  tema?: string;
  tipo: TipoPregunta;
  enunciado: string;
  puntaje: number;
  activa: boolean;
  creadaEn: string;
  opciones: Opcion[];
}
export interface PreguntaRequest {
  cursoId: number;
  tema?: string;
  tipo: TipoPregunta;
  enunciado: string;
  puntaje: number;
  activa?: boolean;
  opciones?: Opcion[];
}

export interface ExamenPreguntaItem {
  preguntaId: number;
  orden: number;
  puntaje: number;
}
export interface ExamenCreate {
  anioId: number;
  periodoId: number;
  seccionId: number;
  cursoId: number;
  titulo: string;
  instrucciones?: string;
  fechaInicio: string;
  fechaFin: string;
  duracionMin: number;
  intentosMax?: number;
  barajarPreguntas?: boolean;
  preguntas: ExamenPreguntaItem[];
}

export interface Examen {
  id: number;
  anioId: number;
  periodoId: number;
  seccionId: number;
  cursoId: number;
  docenteId: number;
  titulo: string;
  instrucciones?: string;
  fechaInicio: string;
  fechaFin: string;
  duracionMin: number;
  intentosMax: number;
  barajarPreguntas: boolean;
  puntajeTotal: number;
  estado: EstadoExamen;
  creadoEn?: string;
  publicadoEn?: string;
}

export interface PreguntaItemDetalle {
  preguntaId: number;
  orden: number;
  puntaje: number;
  tema?: string;
  tipo: TipoPregunta;
  enunciado: string;
  opciones: Opcion[];
}
export interface ExamenDetalle extends Examen {
  preguntas: PreguntaItemDetalle[];
}

export interface IntentoResumen {
  intentoId: number;
  alumnoId: number;
  alumnoNombre: string;
  numeroIntento: number;
  estado: EstadoIntento;
  puntajeObtenido?: number;
  nota?: number;
  requiereRevision?: boolean;
}
export interface ResultadosExamen {
  examenId: number;
  titulo: string;
  puntajeTotal: number;
  totalIntentos: number;
  alumnosEvaluados: number;
  promedioNotas?: number;
  notaMaxima?: number;
  notaMinima?: number;
  intentos: IntentoResumen[];
}

export interface ExamenDisponible {
  examenId: number;
  cursoId: number;
  titulo: string;
  instrucciones?: string;
  fechaInicio: string;
  fechaFin: string;
  duracionMin: number;
  intentosMax: number;
  intentosRealizados: number;
  puedeRendir: boolean;
  puntajeTotal: number;
}

export interface OpcionEstudiante {
  id: number;
  texto: string;
  orden: number;
}
export interface PreguntaEstudiante {
  preguntaId: number;
  orden: number;
  puntaje: number;
  tipo: TipoPregunta;
  enunciado: string;
  opciones: OpcionEstudiante[];
  opcionesRespondidas?: number[];
  textoRespondido?: string;
}
export interface IntentoIniciado {
  intentoId: number;
  examenId: number;
  tituloExamen: string;
  numeroIntento: number;
  iniciadoEn: string;
  venceEn: string;
  puntajeTotal: number;
  preguntas: PreguntaEstudiante[];
}

export interface IntentoResultado {
  intentoId: number;
  examenId: number;
  tituloExamen: string;
  numeroIntento: number;
  estado: EstadoIntento;
  puntajeObtenido?: number;
  puntajeTotal: number;
  nota?: number;
  iniciadoEn: string;
  enviadoEn?: string;
  calificadoEn?: string;
  requiereRevisionAbiertas?: boolean;
}

export interface GuardarRespuesta {
  opcionesIds?: number[];
  textoRespuesta?: string;
}
