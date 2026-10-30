/** Matrícula en línea del sitio público (API sin sesión de matricula-ms). */
export interface PensionOferta { total: number; cuotas: number; mensual: number }
export interface GradoOferta { id: number; nombre: string; vacantes: number; matricula: number | null; pension: PensionOferta | null }
export interface NivelOferta { id: number; nombre: string; grados: GradoOferta[] }
export interface OfertaMatricula { anioId: number; anio: string; abierta: boolean; mensaje: string | null; niveles: NivelOferta[] }

export interface SolicitudMatricula {
  gradoId: number;
  alumno: {
    tipoDocumento: string; numeroDocumento: string; nombres: string; apellidoPaterno: string; apellidoMaterno: string;
    fechaNacimiento: string; sexo: string;
  };
  apoderado: {
    tipoDocumento: string; numeroDocumento: string; nombres: string; apellidos: string; telefono: string; email: string | null; parentesco: string;
  };
  acepto: boolean;
  /** Campo trampa anti-robots: siempre vacío. */
  sitio: string;
}

export interface InicioMatricula {
  referencia: string; token: string; codigo: string; alumno: string; grado: string; seccion: string; turno: string;
  montoMatricula: number; vencePago: string | null;
}

export interface Tarjeta { numero: string; titular: string; vencimiento: string; cvv: string }

export interface PensionResumen { descripcion: string; vence: string; monto: number }
export interface ClaseResumen { dia: string; hora: string; curso: string; docente: string }

export interface ResumenMatricula {
  /** INICIADA → PAGADA (en revisión) → APROBADA | RECHAZADA */
  estado: 'INICIADA' | 'PAGADA' | 'APROBADA' | 'RECHAZADA';
  referencia: string; codigo: string; alumno: string; alumnoDocumento: string; apoderado: string; grado: string; seccion: string; turno: string;
  montoMatricula: number | null; comprobante: string | null; pagadoEn: string | null;
  pensiones: PensionResumen[]; totalPensiones: number; horario: ClaseResumen[];
  usuarioAlumno: string | null; usuarioApoderado: string | null;
  correo: 'ENVIADO' | 'OMITIDO' | 'ERROR' | null; correoDestino: string | null; mensajeCuentas: string | null;
}
