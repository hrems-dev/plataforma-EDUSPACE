export type EstadoAlumno = 'ACTIVO' | 'RETIRADO' | 'EGRESADO' | 'TRASLADADO' | 'INACTIVO';
export type Parentesco = 'PADRE' | 'MADRE' | 'ABUELO' | 'ABUELA' | 'TIO' | 'TIA' | 'HERMANO' | 'TUTOR' | 'OTRO';
export type TipoDocumento = 'PARTIDA_NACIMIENTO' | 'DNI' | 'CERTIFICADO_ESTUDIOS' | 'FICHA_SIAGIE' | 'CARNET_VACUNACION' | 'FOTO' | 'OTRO';

export const ESTADOS_ALUMNO: EstadoAlumno[] = ['ACTIVO', 'INACTIVO', 'RETIRADO', 'TRASLADADO', 'EGRESADO'];
export const PARENTESCOS: Parentesco[] = ['PADRE', 'MADRE', 'ABUELO', 'ABUELA', 'TIO', 'TIA', 'HERMANO', 'TUTOR', 'OTRO'];
export const TIPOS_DOCUMENTO: TipoDocumento[] = ['DNI', 'PARTIDA_NACIMIENTO', 'FICHA_SIAGIE', 'CERTIFICADO_ESTUDIOS', 'CARNET_VACUNACION', 'FOTO', 'OTRO'];

/**
 * Documentos sin los cuales la matrícula se rechaza: deben estar subidos **y verificados**.
 * Es la configuración por defecto de alumnos-ms (`ExpedienteProperties`); si el servicio la cambia, esta lista también.
 */
export const DOCUMENTOS_REQUERIDOS: TipoDocumento[] = ['DNI', 'PARTIDA_NACIMIENTO', 'FICHA_SIAGIE'];

export interface AlumnoResumen {
  id: number;
  codigo: string;
  nombreCompleto: string;
  tipoDocumento?: string;
  numeroDocumento: string;
  estado: EstadoAlumno;
}

export interface Apoderado {
  id: number;
  usuarioId?: number;
  tipoDocumento?: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string;
  telefono?: string;
  telefonoAlterno?: string;
  email?: string;
  direccion?: string;
  ocupacion?: string;
}
export interface ApoderadoRequest {
  tipoDocumento?: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string;
  telefono?: string;
  telefonoAlterno?: string;
  email?: string;
  direccion?: string;
  ocupacion?: string;
}

export interface Vinculo {
  id: number;
  apoderado: Apoderado;
  parentesco: Parentesco;
  esPrincipal: boolean;
  esResponsablePago: boolean;
  viveConAlumno: boolean;
  puedeRecoger: boolean;
}
export interface VinculoUpdate {
  parentesco: Parentesco;
  esPrincipal: boolean;
  esResponsablePago: boolean;
  viveConAlumno: boolean;
  puedeRecoger: boolean;
}
export interface VinculoApoderadoRequest extends Partial<VinculoUpdate> {
  parentesco: Parentesco;
  apoderadoId?: number;
  apoderado?: ApoderadoRequest;
}

export interface Alumno {
  id: number;
  codigo: string;
  usuarioId?: number;
  tipoDocumento?: string;
  numeroDocumento: string;
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno?: string;
  nombreCompleto: string;
  fechaNacimiento: string;
  sexo: string;
  nacionalidad?: string;
  direccion?: string;
  distrito?: string;
  telefono?: string;
  email?: string;
  colegioProcedencia?: string;
  estado: EstadoAlumno;
  apoderados: Vinculo[];
}

export interface AlumnoDatos {
  tipoDocumento?: string;
  numeroDocumento: string;
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno?: string;
  fechaNacimiento: string;
  sexo: string;
  nacionalidad?: string;
  direccion?: string;
  distrito?: string;
  telefono?: string;
  email?: string;
  colegioProcedencia?: string;
}
export interface AlumnoCreate extends AlumnoDatos {
  apoderados: VinculoApoderadoRequest[];
}

export interface Documento {
  id: number;
  alumnoId: number;
  tipo: TipoDocumento;
  nombreArchivo: string;
  contentType: string;
  tamanoBytes: number;
  verificado: boolean;
  subidoEn: string;
}

export interface Descarga {
  url: string;
  expiraEnSegundos: number;
  nombreArchivo: string;
}

export interface Salud {
  alumnoId?: number;
  grupoSanguineo?: string;
  alergias?: string;
  observaciones?: string;
  seguroMedico?: string;
  contactoEmergenciaNombre?: string;
  contactoEmergenciaTelefono?: string;
  actualizadoEn?: string;
}
export type SaludRequest = Omit<Salud, 'alumnoId' | 'actualizadoEn'>;

export interface HistorialEstado {
  id: number;
  estadoAnterior: EstadoAlumno;
  estadoNuevo: EstadoAlumno;
  motivo: string;
  usuarioId: number;
  cambiadoEn: string;
}

export interface FilaImportacion {
  linea: number;
  numeroDocumento?: string;
  creado: boolean;
  alumnoId?: number;
  codigo?: string;
  error?: string;
}
export interface ResultadoImportacion {
  total: number;
  creados: number;
  fallidos: number;
  filas: FilaImportacion[];
}
