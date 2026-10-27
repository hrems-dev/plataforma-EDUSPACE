import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Boletin,
  CierrePeriodo,
  Criterio,
  CriterioRequest,
  GenerarBoletin,
  Libreta,
  NotaPendiente,
  NotasLote,
  Promedio,
  RegistroNotas,
  ResultadoAnual,
  ValidarPesos
} from '../models/calificacion.model';

const API = '/api/v1/calificaciones';

@Injectable({ providedIn: 'root' })
export class CalificacionService {
  private readonly http = inject(HttpClient);

  private tri(periodoId: number, seccionId: number, cursoId: number): HttpParams {
    return new HttpParams().set('periodoId', periodoId).set('seccionId', seccionId).set('cursoId', cursoId);
  }

  // Criterios (docente)
  criterios(periodoId: number, seccionId: number, cursoId: number): Observable<Criterio[]> {
    return this.http.get<Criterio[]>(`${API}/criterios`, { params: this.tri(periodoId, seccionId, cursoId) });
  }
  validarPesos(periodoId: number, seccionId: number, cursoId: number): Observable<ValidarPesos> {
    return this.http.get<ValidarPesos>(`${API}/criterios/validar`, { params: this.tri(periodoId, seccionId, cursoId) });
  }
  crearCriterio(body: CriterioRequest): Observable<Criterio> {
    return this.http.post<Criterio>(`${API}/criterios`, body);
  }
  actualizarCriterio(id: number, body: CriterioRequest): Observable<Criterio> {
    return this.http.put<Criterio>(`${API}/criterios/${id}`, body);
  }
  eliminarCriterio(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/criterios/${id}`);
  }

  // Registro de notas (docente)
  registro(seccionId: number, cursoId: number, periodoId: number): Observable<RegistroNotas> {
    return this.http.get<RegistroNotas>(`${API}/registro`, { params: this.tri(periodoId, seccionId, cursoId) });
  }
  guardarLote(body: NotasLote): Observable<unknown> {
    return this.http.put(`${API}/notas/lote`, body);
  }
  pendientes(seccionId?: number, cursoId?: number): Observable<NotaPendiente[]> {
    let params = new HttpParams();
    if (seccionId) params = params.set('seccionId', seccionId);
    if (cursoId) params = params.set('cursoId', cursoId);
    return this.http.get<NotaPendiente[]>(`${API}/pendientes-asignar`, { params });
  }

  // Secretaría / Admin
  promedios(seccionId: number, periodoId: number): Observable<Promedio[]> {
    return this.http.get<Promedio[]>(`${API}/promedios`, { params: new HttpParams().set('seccionId', seccionId).set('periodoId', periodoId) });
  }
  cerrarPeriodo(seccionId: number, periodoId: number): Observable<CierrePeriodo> {
    return this.http.post<CierrePeriodo>(`${API}/periodos/cerrar`, { seccionId, periodoId });
  }
  reabrirPeriodo(seccionId: number, periodoId: number, motivo: string): Observable<CierrePeriodo> {
    return this.http.post<CierrePeriodo>(`${API}/periodos/reabrir`, { seccionId, periodoId, motivo });
  }
  resultadosAnuales(seccionId: number): Observable<ResultadoAnual[]> {
    return this.http.get<ResultadoAnual[]>(`${API}/resultados-anuales`, { params: new HttpParams().set('seccionId', seccionId) });
  }
  generarBoletines(body: GenerarBoletin): Observable<Boletin[]> {
    return this.http.post<Boletin[]>(`${API}/boletines/generar`, body);
  }
  boletines(alumnoId: number): Observable<Boletin[]> {
    return this.http.get<Boletin[]>(`${API}/boletines`, { params: new HttpParams().set('alumnoId', alumnoId) });
  }
  urlBoletin(id: number): Observable<{ url?: string; urlDescarga?: string }> {
    return this.http.get<{ url?: string; urlDescarga?: string }>(`${API}/boletines/${id}/url`);
  }

  // Familia
  libreta(alumnoId: number, anioId?: number): Observable<Libreta> {
    let params = new HttpParams();
    if (anioId) params = params.set('anioId', anioId);
    return this.http.get<Libreta>(`${API}/alumnos/${alumnoId}/libreta`, { params });
  }
  mias(anioId?: number): Observable<Libreta[]> {
    let params = new HttpParams();
    if (anioId) params = params.set('anioId', anioId);
    return this.http.get<Libreta[]>(`${API}/mias`, { params });
  }
}
