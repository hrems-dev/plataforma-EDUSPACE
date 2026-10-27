export type EstadoCuota = 'PENDIENTE' | 'PARCIAL' | 'PAGADA' | 'VENCIDA' | 'ANULADA';
export type MedioPago = 'EFECTIVO' | 'TARJETA' | 'YAPE_PLIN' | 'TRANSFERENCIA' | 'ONLINE_SIMULADO';
export type TipoComprobante = 'RECIBO' | 'BOLETA';

export const MEDIOS_PAGO: MedioPago[] = ['EFECTIVO', 'TARJETA', 'YAPE_PLIN', 'TRANSFERENCIA'];

export interface Cuota {
  id: number;
  matriculaId: number;
  alumnoId: number;
  anioId: number;
  conceptoId: number;
  conceptoNombre: string;
  conceptoTipo: string;
  descripcion: string;
  numeroCuota: number;
  montoBase: number;
  descuento: number;
  mora: number;
  montoTotal: number;
  saldo: number;
  fechaVencimiento: string;
  estado: EstadoCuota;
  diasAtraso: number;
  avisoEnviado: boolean;
}

export interface EstadoCuenta {
  alumnoId: number;
  alumnoNombre: string;
  responsableNombre: string;
  totalCobrado: number;
  totalPendiente: number;
  totalVencido: number;
  cuotas: Cuota[];
}

export interface Comprobante {
  id: number;
  pagoId: number;
  tipo: TipoComprobante;
  serie: string;
  numero: number;
  numeroCompleto: string;
  total: number;
  estado: 'EMITIDO' | 'ANULADO';
  rutaPdf?: string;
  emitidoEn: string;
}

export interface PagoDetalle {
  id: number;
  cuotaId: number;
  cuotaDescripcion: string;
  montoAplicado: number;
  nuevoSaldoCuota: number;
  nuevoEstadoCuota: string;
}

export interface Pago {
  id: number;
  alumnoId: number;
  pagadorNombre?: string;
  pagadorDocumento?: string;
  montoTotal: number;
  medio: MedioPago;
  referencia?: string;
  estado: 'CONFIRMADO' | 'ANULADO';
  cajaSesionId?: number;
  registradoPor?: number;
  pagadoEn: string;
  idempotencyKey: string;
  comprobante?: Comprobante;
  detalles: PagoDetalle[];
}

export interface RegistrarPago {
  alumnoId: number;
  pagadorNombre?: string;
  pagadorDocumento?: string;
  montoTotal: number;
  medio: MedioPago;
  referencia?: string;
  idempotencyKey: string;
  tipoComprobante?: TipoComprobante;
  detalles: { cuotaId: number; montoAplicado: number }[];
}

export interface Caja {
  id: number;
  cajeroUsuarioId: number;
  aperturaEn: string;
  montoApertura: number;
  cierreEn?: string;
  montoCierreDeclarado?: number;
  montoCierreSistema?: number;
  diferencia?: number;
  estado: 'ABIERTA' | 'CERRADA';
}

export interface CierreCaja {
  cajaId: number;
  cajeroUsuarioId: number;
  aperturaEn: string;
  cierreEn?: string;
  montoApertura: number;
  cobrosEfectivo: number;
  cobrosOtrosMedios: number;
  totalCobrado: number;
  efectivoEsperado: number;
  montoDeclarado?: number;
  diferencia?: number;
  totalesPorMedio: Record<string, number>;
}

export interface FiltroCuotas {
  estado?: EstadoCuota | '';
  alumnoId?: number;
  seccionId?: number;
  vencimiento?: string;
  page?: number;
  size?: number;
}
