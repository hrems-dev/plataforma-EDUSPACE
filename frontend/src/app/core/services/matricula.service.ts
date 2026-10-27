import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/page.model';
import { AlumnoSeccion } from '../models/matricula.model';
import {
  Campania,
  CampaniaRequest,
  Cupo,
  FiltroMatriculas,
  Matricula,
  MatriculaCreate,
  MatriculaDetalle,
  RatificacionRequest,
  RatificacionResultado,
  Requisito,
  TipoRequisito
} from '../models/matricula.model';

const API = '/api/v1/matriculas';

@Injectable({ providedIn: 'root' })
export class MatriculaService {
  private readonly http = inject(HttpClient);

  listar(f: FiltroMatriculas = {}): Observable<Page<Matricula>> {
    let params = new HttpParams().set('page', f.page ?? 0).set('size', f.size ?? 15);
    if (f.anioId) params = params.set('anioId', f.anioId);
    if (f.gradoId) params = params.set('gradoId', f.gradoId);
    if (f.seccionId) params = params.set('seccionId', f.seccionId);
    if (f.estado) params = params.set('estado', f.estado);
    if (f.texto) params = params.set('texto', f.texto);
    return this.http.get<Page<Matricula>>(API, { params });
  }

  /** Alumnos de una sección (staff y docentes). */
  alumnosDeSeccion(seccionId: number): Observable<AlumnoSeccion[]> {
    return this.http.get<AlumnoSeccion[]>(`${API}/seccion/${seccionId}/alumnos`);
  }

  detalle(id: number): Observable<MatriculaDetalle> {
    return this.http.get<MatriculaDetalle>(`${API}/${id}`);
  }

  crear(body: MatriculaCreate): Observable<Matricula> {
    return this.http.post<Matricula>(API, body);
  }

  marcarRequisito(id: number, tipo: TipoRequisito, cumplido: boolean, observacion?: string): Observable<Requisito> {
    return this.http.put<Requisito>(`${API}/${id}/requisitos/${tipo}`, { cumplido, observacion });
  }

  anular(id: number, motivo: string): Observable<Matricula> {
    return this.http.patch<Matricula>(`${API}/${id}/anular`, { motivo });
  }

  retirar(id: number, motivo: string): Observable<Matricula> {
    return this.http.patch<Matricula>(`${API}/${id}/retiro`, { motivo });
  }

  trasladar(id: number, nuevaSeccionId: number, motivo: string): Observable<Matricula> {
    return this.http.patch<Matricula>(`${API}/${id}/traslado`, { nuevaSeccionId, motivo });
  }

  ratificar(body: RatificacionRequest): Observable<RatificacionResultado> {
    return this.http.post<RatificacionResultado>(`${API}/ratificacion`, body);
  }

  // Campañas
  campanias(anioId?: number): Observable<Campania[]> {
    let params = new HttpParams();
    if (anioId) params = params.set('anioId', anioId);
    return this.http.get<Campania[]>(`${API}/campanias`, { params });
  }

  crearCampania(body: CampaniaRequest): Observable<Campania> {
    return this.http.post<Campania>(`${API}/campanias`, body);
  }

  actualizarCampania(id: number, body: CampaniaRequest): Observable<Campania> {
    return this.http.put<Campania>(`${API}/campanias/${id}`, body);
  }

  abrirCampania(id: number): Observable<Campania> {
    return this.http.patch<Campania>(`${API}/campanias/${id}/abrir`, {});
  }

  cerrarCampania(id: number): Observable<Campania> {
    return this.http.patch<Campania>(`${API}/campanias/${id}/cerrar`, {});
  }

  cupos(anioId: number, gradoId?: number): Observable<Cupo[]> {
    let params = new HttpParams().set('anioId', anioId);
    if (gradoId) params = params.set('gradoId', gradoId);
    return this.http.get<Cupo[]>(`${API}/cupos`, { params });
  }
}
