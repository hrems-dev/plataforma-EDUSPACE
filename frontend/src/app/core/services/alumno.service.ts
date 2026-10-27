import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  Alumno,
  AlumnoCreate,
  AlumnoDatos,
  AlumnoResumen,
  Apoderado,
  ApoderadoRequest,
  Descarga,
  Documento,
  EstadoAlumno,
  HistorialEstado,
  ResultadoImportacion,
  Salud,
  SaludRequest,
  TipoDocumento,
  Vinculo,
  VinculoApoderadoRequest,
  VinculoUpdate
} from '../models/alumno.model';
import { Page } from '../models/page.model';

const API = '/api/v1';

interface PaginaConTotal<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  total: number;
  totalPaginas: number;
}

/** alumnos-ms devuelve `total`; se normaliza a `totalElementos` como el resto de las páginas. */
const normalizar = <T>(p: PaginaConTotal<T>): Page<T> => ({
  contenido: p.contenido,
  pagina: p.pagina,
  tamano: p.tamano,
  totalElementos: p.total,
  totalPaginas: p.totalPaginas
});

@Injectable({ providedIn: 'root' })
export class AlumnoService {
  private readonly http = inject(HttpClient);

  listar(q = '', estado: EstadoAlumno | '' = '', page = 0, size = 15): Observable<Page<AlumnoResumen>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (estado) params = params.set('estado', estado);
    return this.http.get<PaginaConTotal<AlumnoResumen>>(`${API}/alumnos`, { params }).pipe(map(normalizar));
  }

  obtener(id: number): Observable<Alumno> {
    return this.http.get<Alumno>(`${API}/alumnos/${id}`);
  }

  crear(body: AlumnoCreate): Observable<Alumno> {
    return this.http.post<Alumno>(`${API}/alumnos`, body);
  }

  actualizar(id: number, body: AlumnoDatos): Observable<Alumno> {
    return this.http.put<Alumno>(`${API}/alumnos/${id}`, body);
  }

  cambiarEstado(id: number, estado: EstadoAlumno, motivo: string): Observable<AlumnoResumen> {
    return this.http.patch<AlumnoResumen>(`${API}/alumnos/${id}/estado`, { estado, motivo });
  }

  historialEstado(id: number): Observable<HistorialEstado[]> {
    return this.http.get<HistorialEstado[]>(`${API}/alumnos/${id}/estado/historial`);
  }

  // Apoderados
  buscarApoderados(q: string, page = 0, size = 10): Observable<Page<Apoderado>> {
    const params = new HttpParams().set('q', q).set('page', page).set('size', size);
    return this.http.get<PaginaConTotal<Apoderado>>(`${API}/apoderados`, { params }).pipe(map(normalizar));
  }

  vinculos(alumnoId: number): Observable<Vinculo[]> {
    return this.http.get<Vinculo[]>(`${API}/alumnos/${alumnoId}/apoderados`);
  }

  agregarVinculo(alumnoId: number, body: VinculoApoderadoRequest): Observable<Vinculo> {
    return this.http.post<Vinculo>(`${API}/alumnos/${alumnoId}/apoderados`, body);
  }

  actualizarVinculo(alumnoId: number, vinculoId: number, body: VinculoUpdate): Observable<Vinculo> {
    return this.http.put<Vinculo>(`${API}/alumnos/${alumnoId}/apoderados/${vinculoId}`, body);
  }

  quitarVinculo(alumnoId: number, vinculoId: number): Observable<void> {
    return this.http.delete<void>(`${API}/alumnos/${alumnoId}/apoderados/${vinculoId}`);
  }

  actualizarApoderado(id: number, body: ApoderadoRequest): Observable<Apoderado> {
    return this.http.put<Apoderado>(`${API}/apoderados/${id}`, body);
  }

  // Documentos
  documentos(alumnoId: number): Observable<Documento[]> {
    return this.http.get<Documento[]>(`${API}/alumnos/${alumnoId}/documentos`);
  }

  subirDocumento(alumnoId: number, tipo: TipoDocumento, archivo: File): Observable<Documento> {
    const form = new FormData();
    form.append('archivo', archivo);
    return this.http.post<Documento>(`${API}/alumnos/${alumnoId}/documentos`, form, { params: new HttpParams().set('tipo', tipo) });
  }

  verificarDocumento(alumnoId: number, documentoId: number, verificado: boolean): Observable<Documento> {
    return this.http.patch<Documento>(`${API}/alumnos/${alumnoId}/documentos/${documentoId}/verificado`, { verificado });
  }

  descargarDocumento(alumnoId: number, documentoId: number): Observable<Descarga> {
    return this.http.get<Descarga>(`${API}/alumnos/${alumnoId}/documentos/${documentoId}/descarga`);
  }

  eliminarDocumento(alumnoId: number, documentoId: number): Observable<void> {
    return this.http.delete<void>(`${API}/alumnos/${alumnoId}/documentos/${documentoId}`);
  }

  // Salud (la ficha no existe hasta que se guarda por primera vez: 404)
  salud(alumnoId: number): Observable<Salud> {
    return this.http.get<Salud>(`${API}/alumnos/${alumnoId}/salud`);
  }

  guardarSalud(alumnoId: number, body: SaludRequest): Observable<Salud> {
    return this.http.put<Salud>(`${API}/alumnos/${alumnoId}/salud`, body);
  }

  /** Importación masiva por CSV: cada fila se procesa por separado y el resultado dice cuáles fallaron y por qué. */
  importar(archivo: File): Observable<ResultadoImportacion> {
    const form = new FormData();
    form.append('archivo', archivo);
    return this.http.post<ResultadoImportacion>(`${API}/alumnos/importacion`, form);
  }
}
