export type TipoConcepto = 'MATRICULA' | 'PENSION' | 'OTRO';
export type TipoDescuento = 'PORCENTAJE' | 'MONTO_FIJO';
export type AplicaA = 'MATRICULA' | 'PENSION' | 'TODO';

export const TIPOS_CONCEPTO: TipoConcepto[] = ['MATRICULA', 'PENSION', 'OTRO'];
export const TIPOS_DESCUENTO: TipoDescuento[] = ['PORCENTAJE', 'MONTO_FIJO'];
export const APLICA_A: AplicaA[] = ['MATRICULA', 'PENSION', 'TODO'];

export interface Concepto {
  id: number;
  codigo: string;
  nombre: string;
  tipo: TipoConcepto;
  activo: boolean;
}
export type ConceptoRequest = Omit<Concepto, 'id'>;

export interface Tarifa {
  id: number;
  anioId: number;
  nivelId: number;
  gradoId?: number;
  conceptoId: number;
  conceptoCodigo: string;
  conceptoNombre: string;
  monto: number;
  numCuotas: number;
  mesInicio: number;
  diaVencimiento: number;
  activo: boolean;
}
export interface TarifaRequest {
  anioId: number;
  nivelId: number;
  gradoId?: number | null;
  conceptoId: number;
  monto: number;
  numCuotas: number;
  mesInicio: number;
  diaVencimiento: number;
  activo: boolean;
}

export interface Descuento {
  id: number;
  nombre: string;
  tipo: TipoDescuento;
  valor: number;
  aplicaA: AplicaA;
  activo: boolean;
}
export type DescuentoRequest = Omit<Descuento, 'id'>;

export interface AlertaAdmin {
  id: number;
  tipo: string;
  matriculaId?: number;
  anioId?: number;
  nivelId?: number;
  gradoId?: number;
  detalle: string;
  resuelta: boolean;
  creadaEn: string;
  resueltaEn?: string;
}

export interface DetalleMoroso {
  alumnoId: number;
  alumnoNombre: string;
  matriculaId: number;
  responsableNombre: string;
  saldoTotalVencido: number;
  cuotasVencidas: number;
}

export interface Morosos {
  seccionId?: number;
  gradoId?: number;
  totalAlumnosMorosos: number;
  totalDeudaAcumulada: number;
  listaMorosos: DetalleMoroso[];
}
