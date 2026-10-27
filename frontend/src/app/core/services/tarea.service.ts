import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, switchMap } from 'rxjs';
import { Page } from '../models/page.model';
import {
  ArchivoRef,
  Entrega,
  EntregaAlumno,
  EstadoTarea,
  Tarea,
  TareaCreate,
  TareaPendiente,
  TareaUpdate,
  UrlSubida
} from '../models/tarea.model';

const API = '/api/v1/tareas';

@Injectable({ providedIn: 'root' })
export class TareaService {
  private readonly http = inject(HttpClient);

  listar(f: { seccionId?: number; cursoId?: number; periodoId?: number; estado?: EstadoTarea | '' } = {}, page = 0, size = 15): Observable<Page<Tarea>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (f.seccionId) params = params.set('seccionId', f.seccionId);
    if (f.cursoId) params = params.set('cursoId', f.cursoId);
    if (f.periodoId) params = params.set('periodoId', f.periodoId);
    if (f.estado) params = params.set('estado', f.estado);
    return this.http.get<Page<Tarea>>(API, { params });
  }
  detalle(id: number): Observable<Tarea> {
    return this.http.get<Tarea>(`${API}/${id}`);
  }
  crear(body: TareaCreate): Observable<Tarea> {
    return this.http.post<Tarea>(API, body);
  }
  actualizar(id: number, body: TareaUpdate): Observable<Tarea> {
    return this.http.put<Tarea>(`${API}/${id}`, body);
  }
  publicar(id: number): Observable<Tarea> {
    return this.http.patch<Tarea>(`${API}/${id}/publicar`, {});
  }
  cerrar(id: number): Observable<Tarea> {
    return this.http.patch<Tarea>(`${API}/${id}/cerrar`, {});
  }

  // Docente: entregas y calificación
  entregas(id: number): Observable<EntregaAlumno[]> {
    return this.http.get<EntregaAlumno[]>(`${API}/${id}/entregas`);
  }
  calificar(entregaId: number, nota: number, retroalimentacion?: string): Observable<Entrega> {
    return this.http.patch<Entrega>(`${API}/entregas/${entregaId}/calificar`, { nota, retroalimentacion });
  }
  devolver(entregaId: number, retroalimentacion: string): Observable<Entrega> {
    return this.http.patch<Entrega>(`${API}/entregas/${entregaId}/devolver`, { retroalimentacion });
  }
  urlArchivoEntrega(entregaId: number, archivoId: number): Observable<{ urlDescarga: string; nombreArchivo: string }> {
    return this.http.get<{ urlDescarga: string; nombreArchivo: string }>(`${API}/entregas/${entregaId}/archivos/${archivoId}/url`);
  }
  urlAdjunto(tareaId: number, adjuntoId: number): Observable<{ urlDescarga: string; nombreArchivo: string }> {
    return this.http.get<{ urlDescarga: string; nombreArchivo: string }>(`${API}/${tareaId}/adjuntos/${adjuntoId}/url`);
  }

  // Alumno
  /** Tareas de mis hijos (apoderado) o mías (alumno), con el estado de la entrega. */
  mias(): Observable<TareaPendiente[]> {
    return this.http.get<TareaPendiente[]>(`${API}/mias`);
  }
  pendientes(): Observable<TareaPendiente[]> {
    return this.http.get<TareaPendiente[]>(`${API}/mias/pendientes`);
  }
  entregar(tareaId: number, comentario: string | undefined, archivos: ArchivoRef[]): Observable<Entrega> {
    return this.http.post<Entrega>(`${API}/${tareaId}/entregas`, { comentario, archivos });
  }

  /**
   * Sube un archivo de la entrega: pide una URL firmada de MinIO, sube el archivo directo y devuelve la referencia
   * para incluirla en la entrega (el archivo no pasa por el backend).
   */
  subirArchivoEntrega(tareaId: number, archivo: File): Observable<ArchivoRef> {
    const contentType = archivo.type || 'application/octet-stream';
    return this.http
      .post<UrlSubida>(`${API}/${tareaId}/entregas/archivo-url`, { nombreArchivo: archivo.name, contentType, tamanoBytes: archivo.size })
      .pipe(
        switchMap(u => this.http.put(u.uploadUrl, archivo, { headers: { 'Content-Type': contentType }, responseType: 'text' }).pipe(map(() => u))),
        map(u => ({ nombreArchivo: archivo.name, ruta: u.ruta, contentType, tamanoBytes: archivo.size }))
      );
  }
}
