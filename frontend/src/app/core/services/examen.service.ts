import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  EstadoExamen,
  Examen,
  ExamenCreate,
  ExamenDetalle,
  ExamenDisponible,
  GuardarRespuesta,
  IntentoIniciado,
  IntentoResultado,
  Pregunta,
  PreguntaRequest,
  ResultadosExamen,
  TipoPregunta
} from '../models/examen.model';
import { Page } from '../models/page.model';

const API = '/api/v1/examenes';

@Injectable({ providedIn: 'root' })
export class ExamenService {
  private readonly http = inject(HttpClient);

  // Banco de preguntas (docente)
  preguntas(cursoId: number, tema?: string, tipo?: TipoPregunta | '', page = 0, size = 15): Observable<Page<Pregunta>> {
    let params = new HttpParams().set('cursoId', cursoId).set('page', page).set('size', size);
    if (tema) params = params.set('tema', tema);
    if (tipo) params = params.set('tipo', tipo);
    return this.http.get<Page<Pregunta>>(`${API}/preguntas`, { params });
  }
  crearPregunta(body: PreguntaRequest): Observable<Pregunta> {
    return this.http.post<Pregunta>(`${API}/preguntas`, body);
  }
  actualizarPregunta(id: number, body: PreguntaRequest): Observable<Pregunta> {
    return this.http.put<Pregunta>(`${API}/preguntas/${id}`, body);
  }
  eliminarPregunta(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/preguntas/${id}`);
  }

  // Exámenes (docente)
  listar(f: { seccionId?: number; cursoId?: number; docenteId?: number; estado?: EstadoExamen | '' } = {}, page = 0, size = 15): Observable<Page<Examen>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (f.seccionId) params = params.set('seccionId', f.seccionId);
    if (f.cursoId) params = params.set('cursoId', f.cursoId);
    if (f.docenteId) params = params.set('docenteId', f.docenteId);
    if (f.estado) params = params.set('estado', f.estado);
    return this.http.get<Page<Examen>>(API, { params });
  }
  detalle(id: number): Observable<ExamenDetalle> {
    return this.http.get<ExamenDetalle>(`${API}/${id}`);
  }
  crear(body: ExamenCreate): Observable<Examen> {
    return this.http.post<Examen>(API, body);
  }
  actualizar(id: number, body: ExamenCreate): Observable<Examen> {
    return this.http.put<Examen>(`${API}/${id}`, body);
  }
  publicar(id: number): Observable<Examen> {
    return this.http.patch<Examen>(`${API}/${id}/publicar`, {});
  }
  cerrar(id: number): Observable<Examen> {
    return this.http.patch<Examen>(`${API}/${id}/cerrar`, {});
  }
  resultados(id: number): Observable<ResultadosExamen> {
    return this.http.get<ResultadosExamen>(`${API}/${id}/resultados`);
  }
  pendientesRevision(id: number): Observable<unknown[]> {
    return this.http.get<unknown[]>(`${API}/${id}/pendientes-revision`);
  }
  revisarRespuesta(respuestaId: number, puntajeObtenido: number): Observable<unknown> {
    return this.http.patch(`${API}/respuestas/${respuestaId}/revisar`, { puntajeObtenido });
  }

  // Alumno / apoderado
  disponibles(): Observable<ExamenDisponible[]> {
    return this.http.get<ExamenDisponible[]>(`${API}/disponibles`);
  }
  mios(): Observable<unknown[]> {
    return this.http.get<unknown[]>(`${API}/mias`);
  }
  iniciarIntento(examenId: number): Observable<IntentoIniciado> {
    return this.http.post<IntentoIniciado>(`${API}/${examenId}/intentos`, {});
  }
  guardarRespuesta(intentoId: number, preguntaId: number, body: GuardarRespuesta): Observable<unknown> {
    return this.http.put(`${API}/intentos/${intentoId}/respuestas/${preguntaId}`, body);
  }
  enviar(intentoId: number): Observable<IntentoResultado> {
    return this.http.post<IntentoResultado>(`${API}/intentos/${intentoId}/enviar`, {});
  }
  resultado(intentoId: number): Observable<IntentoResultado> {
    return this.http.get<IntentoResultado>(`${API}/intentos/${intentoId}/resultado`);
  }
}
