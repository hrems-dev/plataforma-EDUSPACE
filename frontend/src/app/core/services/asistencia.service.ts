import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ArchivoUrl,
  AsistenciaHijo,
  EstadoJustificacion,
  ItemRegistro,
  Justificacion,
  JustificacionCreate,
  ReporteSeccion,
  ResumenAlumno,
  Sesion,
  SesionCreate
} from '../models/asistencia.model';
import { Page } from '../models/page.model';

const API = '/api/v1/asistencia';

@Injectable({ providedIn: 'root' })
export class AsistenciaService {
  private readonly http = inject(HttpClient);

  // Docente
  sesiones(seccionId: number, fecha: string): Observable<Sesion[]> {
    return this.http.get<Sesion[]>(`${API}/sesiones`, { params: new HttpParams().set('seccionId', seccionId).set('fecha', fecha) });
  }
  /** Abre la sesión del día (idempotente: si ya existe, devuelve la misma). */
  abrirSesion(body: SesionCreate): Observable<Sesion> {
    return this.http.post<Sesion>(`${API}/sesiones`, body);
  }
  guardarRegistro(sesionId: number, items: ItemRegistro[]): Observable<Sesion> {
    return this.http.put<Sesion>(`${API}/sesiones/${sesionId}/registro`, { items });
  }
  cerrarSesion(sesionId: number): Observable<Sesion> {
    return this.http.patch<Sesion>(`${API}/sesiones/${sesionId}/cerrar`, {});
  }
  corregir(detalleId: number, estado: string, motivo: string, minutosTarde?: number): Observable<unknown> {
    return this.http.patch(`${API}/detalle/${detalleId}`, { estado, motivo, minutosTarde });
  }

  // Reportes
  reporteSeccion(seccionId: number, desde?: string, hasta?: string): Observable<ReporteSeccion> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    return this.http.get<ReporteSeccion>(`${API}/secciones/${seccionId}/reporte`, { params });
  }
  resumenAlumno(alumnoId: number, desde?: string, hasta?: string, periodoId?: number): Observable<ResumenAlumno> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    if (periodoId) params = params.set('periodoId', periodoId);
    return this.http.get<ResumenAlumno>(`${API}/alumnos/${alumnoId}/resumen`, { params });
  }

  // Familia
  mias(): Observable<AsistenciaHijo[]> {
    return this.http.get<AsistenciaHijo[]>(`${API}/mias`);
  }
  misJustificaciones(): Observable<Justificacion[]> {
    return this.http.get<Justificacion[]>(`${API}/justificaciones/mias`);
  }
  solicitarJustificacion(body: JustificacionCreate): Observable<Justificacion> {
    return this.http.post<Justificacion>(`${API}/justificaciones`, body);
  }
  urlSubidaSustento(id: number, nombreArchivo: string, contentType: string): Observable<ArchivoUrl> {
    return this.http.post<ArchivoUrl>(`${API}/justificaciones/${id}/archivo-url`, { nombreArchivo, contentType });
  }

  // Secretaría
  bandeja(estado?: EstadoJustificacion, page = 0, size = 15): Observable<Page<Justificacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) params = params.set('estado', estado);
    return this.http.get<Page<Justificacion>>(`${API}/justificaciones`, { params });
  }
  resolver(id: number, resolucion: 'APROBADA' | 'RECHAZADA', comentario?: string): Observable<Justificacion> {
    return this.http.patch<Justificacion>(`${API}/justificaciones/${id}/resolver`, { resolucion, comentario });
  }
  archivoSustento(id: number): Observable<{ url?: string; urlDescarga?: string }> {
    return this.http.get<{ url?: string; urlDescarga?: string }>(`${API}/justificaciones/${id}/archivo`);
  }
}
