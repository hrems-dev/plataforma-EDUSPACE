export type EstadoPlan = 'PLANIFICADO' | 'ACTIVO' | 'CERRADO';
export type Turno = 'MANANA' | 'TARDE';
export type TipoAula = 'AULA' | 'LABORATORIO' | 'COMPUTO' | 'AUDITORIO' | 'PATIO' | 'OTRO';

export const TIPOS_AULA: TipoAula[] = ['AULA', 'LABORATORIO', 'COMPUTO', 'AUDITORIO', 'PATIO', 'OTRO'];

export interface Anio {
  id: number;
  nombre: string;
  fechaInicio: string;
  fechaFin: string;
  estado: EstadoPlan;
}
export type AnioRequest = Pick<Anio, 'nombre' | 'fechaInicio' | 'fechaFin'>;

export interface Periodo {
  id: number;
  anioId: number;
  numero: number;
  nombre: string;
  fechaInicio: string;
  fechaFin: string;
  estado: EstadoPlan;
}
export type PeriodoRequest = Omit<Periodo, 'id' | 'estado'>;

export interface Nivel {
  id: number;
  codigo: string;
  nombre: string;
  orden: number;
  activo: boolean;
}

export interface Grado {
  id: number;
  nivelId: number;
  nivelNombre: string;
  numero: number;
  nombre: string;
  activo: boolean;
}

export interface Seccion {
  id: number;
  anioId: number;
  anioNombre: string;
  gradoId: number;
  gradoNombre: string;
  nivelId: number;
  aulaId?: number;
  aulaNombre?: string;
  nombre: string;
  turno: Turno;
  capacidad: number;
  tutorDocenteId?: number;
  tutorNombre?: string;
  activo: boolean;
}
export interface SeccionRequest {
  anioId: number;
  gradoId: number;
  aulaId?: number | null;
  nombre: string;
  turno: Turno;
  capacidad: number;
  tutorDocenteId?: number | null;
  activo: boolean;
}

export interface Aula {
  id: number;
  codigo: string;
  nombre: string;
  tipo: TipoAula;
  capacidad: number;
  activo: boolean;
}
export type AulaRequest = Omit<Aula, 'id'>;

export interface Curso {
  id: number;
  codigo: string;
  nombre: string;
  area?: string;
  activo: boolean;
}
export type CursoRequest = Omit<Curso, 'id'>;

export interface Docente {
  id: number;
  usuarioId?: number;
  tipoDocumento?: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string;
  email?: string;
  telefono?: string;
  especialidad?: string;
  estado: 'ACTIVO' | 'INACTIVO';
}
export type DocenteRequest = Omit<Docente, 'id' | 'usuarioId' | 'estado'>;

/** Lo que devuelve GET /docentes/mi (la ficha completa se consulta por /docentes/{id}). */
export interface DocenteMio {
  docenteId: number;
  nombre: string;
  estado: 'ACTIVO' | 'INACTIVO';
}

// --- Malla, asignaciones, bloques, horarios y feriados ---
export interface MallaItem {
  id: number;
  cursoId: number;
  cursoCodigo: string;
  cursoNombre: string;
  horasSemanales: number;
}
export interface MallaGrado {
  gradoId: number;
  gradoNombre: string;
  totalHoras: number;
  cursos: MallaItem[];
}

export interface Bloque {
  id: number;
  turno: Turno;
  orden: number;
  horaInicio: string;
  horaFin: string;
  esReceso: boolean;
}
export type BloqueRequest = Omit<Bloque, 'id'>;

export interface Asignacion {
  id: number;
  seccionId: number;
  seccionNombre: string;
  cursoId: number;
  cursoNombre: string;
  docenteId: number;
  docenteNombre: string;
}
export interface AsignacionRequest {
  seccionId: number;
  cursoId: number;
  docenteId: number;
}

export interface ClaseHorario {
  id: number;
  asignacionId: number;
  seccionId: number;
  seccionNombre: string;
  cursoId: number;
  cursoNombre: string;
  docenteId: number;
  docenteNombre: string;
  bloqueId: number;
  bloqueOrden: number;
  horaInicio: string;
  horaFin: string;
  aulaId?: number;
  aulaNombre?: string;
  diaSemana: number;
}
export interface HorarioRequest {
  asignacionId: number;
  bloqueId: number;
  aulaId?: number | null;
  diaSemana: number;
}
export interface HorarioSeccion {
  seccionId: number;
  seccionNombre: string;
  turno: Turno;
  bloques: Bloque[];
  clases: ClaseHorario[];
}
export interface HorarioDocente {
  docenteId: number;
  docenteNombre: string;
  clases: ClaseHorario[];
}
export interface CursoHoras {
  cursoId: number;
  cursoNombre: string;
  horasRequeridas: number;
  horasProgramadas: number;
  estado: string;
}
export interface ValidacionHorario {
  valido: boolean;
  choques: string[];
  cursosIncompletos: CursoHoras[];
}
export interface Publicacion {
  id: number;
  seccionId: number;
  version: number;
  publicadoPor: number;
  publicadoEn: string;
}

export interface Feriado {
  id: number;
  anioId: number;
  fecha: string;
  descripcion: string;
}
export type FeriadoRequest = Omit<Feriado, 'id'>;

export const DIAS_SEMANA = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
