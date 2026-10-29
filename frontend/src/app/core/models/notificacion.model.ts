export type CanalNotificacion = 'IN_APP' | 'EMAIL' | 'PUSH';
export const CANALES: CanalNotificacion[] = ['IN_APP', 'EMAIL', 'PUSH'];

export interface Notificacion {
  id: number;
  tipo: string;
  titulo: string;
  mensaje: string;
  datos?: Record<string, unknown>;
  leida: boolean;
  leidaEn?: string;
  creadaEn: string;
}

export interface Preferencia {
  tipo: string;
  canal: CanalNotificacion;
  habilitada: boolean;
}

export interface Plantilla {
  id: number;
  codigo: string;
  canal: CanalNotificacion;
  asunto?: string;
  cuerpo: string;
  activa: boolean;
  actualizadoEn?: string;
}

export interface PlantillaCrear {
  codigo: string;
  canal: CanalNotificacion;
  asunto?: string;
  cuerpo: string;
  activa: boolean;
}

export type EstadoEnvio = 'PENDIENTE' | 'ENVIADA' | 'FALLIDA' | 'DESCARTADA';

/** Una notificación vista por el ADMIN (cualquier destinatario y canal). */
export interface NotificacionAdmin {
  id: number;
  usuarioId: number;
  tipo: string;
  canal: CanalNotificacion;
  estado: EstadoEnvio;
  titulo: string;
  mensaje: string;
  leida: boolean;
  creadaEn: string;
  enviadaEn?: string;
}

export interface FiltroAvisos {
  tipo?: string;
  canal?: CanalNotificacion | '';
  estado?: EstadoEnvio | '';
  usuarioId?: number;
  texto?: string;
}

export interface EnvioManual {
  usuarioIds: number[];
  titulo: string;
  mensaje: string;
  canales: CanalNotificacion[];
}

export interface EnvioManualResultado {
  creadas: number;
  omitidas: number;
}

/** Tipos de aviso que genera el sistema, con los canales que tienen plantilla (notificaciones-ms). */
export const TIPOS_NOTIFICACION: { codigo: string; etiqueta: string; canales: CanalNotificacion[] }[] = [
  { codigo: 'ASISTENCIA_FALTA', etiqueta: 'Falta registrada', canales: ['IN_APP'] },
  { codigo: 'BOLETIN_GENERADO', etiqueta: 'Boletín disponible', canales: ['IN_APP', 'EMAIL'] },
  { codigo: 'EXAMEN_CALIFICADO', etiqueta: 'Examen calificado', canales: ['IN_APP'] },
  { codigo: 'EXAMEN_PUBLICADO', etiqueta: 'Nuevo examen', canales: ['IN_APP'] },
  { codigo: 'HORARIO_PUBLICADO', etiqueta: 'Horario publicado', canales: ['IN_APP'] },
  { codigo: 'JUSTIFICACION_RESUELTA', etiqueta: 'Justificación resuelta', canales: ['IN_APP'] },
  { codigo: 'MATRICULA_ANULADA', etiqueta: 'Matrícula anulada', canales: ['IN_APP'] },
  { codigo: 'MATRICULA_CONFIRMADA', etiqueta: 'Matrícula confirmada', canales: ['IN_APP', 'EMAIL'] },
  { codigo: 'MATRICULA_CREADA', etiqueta: 'Pre-matrícula creada', canales: ['IN_APP'] },
  { codigo: 'PAGO_CONFIRMADO', etiqueta: 'Pago confirmado', canales: ['IN_APP', 'EMAIL'] },
  { codigo: 'PAGO_POR_VENCER', etiqueta: 'Cuota por vencer', canales: ['IN_APP', 'EMAIL'] },
  { codigo: 'PAGO_VENCIDO', etiqueta: 'Cuota vencida', canales: ['IN_APP', 'EMAIL'] },
  { codigo: 'TAREA_CALIFICADA', etiqueta: 'Tarea calificada', canales: ['IN_APP'] },
  { codigo: 'TAREA_PUBLICADA', etiqueta: 'Nueva tarea', canales: ['IN_APP'] },
  { codigo: 'TAREA_RECORDATORIO', etiqueta: 'Recordatorio de tarea', canales: ['IN_APP'] }
];
