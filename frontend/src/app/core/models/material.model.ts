export type TipoMaterial = 'ARCHIVO' | 'ENLACE';

export interface Material {
  id: number;
  seccionId: number;
  cursoId: number;
  docenteId: number;
  titulo: string;
  descripcion?: string;
  tipo: TipoMaterial;
  enlace?: string;
  nombreArchivo?: string;
  contentType?: string;
  tamanoBytes?: number;
  creadoEn: string;
}

/** Tipos que acepta el servidor (los mismos de las entregas de tareas), hasta 10 MB por archivo. */
export const EXTENSIONES_MATERIAL = '.pdf,.docx,.pptx,.xlsx,.jpg,.jpeg,.png,.zip,.txt';
