import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, switchMap } from 'rxjs';
import { Material } from '../models/material.model';

const API = '/api/v1/tareas/materiales';

interface UrlSubida { uploadUrl: string; ruta: string; expiraMinutos: number }

/** Materiales de curso: el docente sube archivos o enlaces; alumnos y apoderados de la sección los ven. */
@Injectable({ providedIn: 'root' })
export class MaterialService {
  private readonly http = inject(HttpClient);

  listar(seccionId: number, cursoId: number): Observable<Material[]> {
    return this.http.get<Material[]>(API, { params: new HttpParams().set('seccionId', seccionId).set('cursoId', cursoId) });
  }

  /** Sube el archivo directo al almacenamiento (URL firmada) y registra el material. */
  publicarArchivo(seccionId: number, cursoId: number, titulo: string, descripcion: string, archivo: File): Observable<Material> {
    const contentType = archivo.type || tipoPorExtension(archivo.name);
    return this.http.post<UrlSubida>(`${API}/archivo-url`, { seccionId, cursoId, nombreArchivo: archivo.name, contentType, tamanoBytes: archivo.size }).pipe(
      switchMap(u => this.http.put(u.uploadUrl, archivo, { headers: { 'Content-Type': contentType }, responseType: 'text' }).pipe(map(() => u))),
      switchMap(u => this.http.post<Material>(API, {
        seccionId, cursoId, titulo, descripcion: descripcion || null, tipo: 'ARCHIVO',
        archivo: { nombreArchivo: archivo.name, ruta: u.ruta, contentType, tamanoBytes: archivo.size }
      }))
    );
  }

  publicarEnlace(seccionId: number, cursoId: number, titulo: string, descripcion: string, enlace: string): Observable<Material> {
    return this.http.post<Material>(API, { seccionId, cursoId, titulo, descripcion: descripcion || null, tipo: 'ENLACE', enlace });
  }

  urlDescarga(id: number): Observable<{ urlDescarga: string; nombreArchivo: string }> {
    return this.http.get<{ urlDescarga: string; nombreArchivo: string }>(`${API}/${id}/url`);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/${id}`);
  }
}

/** Cuando el navegador no informa el tipo (p. ej. .docx en algunos sistemas) se deduce de la extensión. */
function tipoPorExtension(nombre: string): string {
  const ext = nombre.toLowerCase().split('.').pop() ?? '';
  const mapa: Record<string, string> = {
    pdf: 'application/pdf', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', zip: 'application/zip', txt: 'text/plain',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  };
  return mapa[ext] ?? 'application/octet-stream';
}
