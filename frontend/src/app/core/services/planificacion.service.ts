import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Page } from '../models/page.model';
import {
  Asignacion,
  AsignacionRequest,
  DocenteMio,
  Bloque,
  BloqueRequest,
  Feriado,
  FeriadoRequest,
  HorarioDocente,
  HorarioRequest,
  HorarioSeccion,
  ClaseHorario,
  MallaGrado,
  MallaItem,
  Publicacion,
  ValidacionHorario,
  Anio,
  AnioRequest,
  Aula,
  AulaRequest,
  Curso,
  CursoRequest,
  Docente,
  DocenteRequest,
  EstadoPlan,
  Grado,
  Nivel,
  Periodo,
  PeriodoRequest,
  Seccion,
  SeccionRequest,
  TipoAula,
  GenerarHorarioRequest,
  MallaLoteRequest,
  MallaLoteResultado,
  PreparacionSeccion,
  ResultadoGeneracion
} from '../models/planificacion.model';

const API = '/api/v1/planificacion';

@Injectable({ providedIn: 'root' })
export class PlanificacionService {
  private readonly http = inject(HttpClient);

  // Años académicos
  anios(): Observable<Anio[]> {
    return this.http.get<Anio[]>(`${API}/anios`);
  }
  anioActivo(): Observable<Anio> {
    return this.http.get<Anio>(`${API}/anios/activo`);
  }
  crearAnio(body: AnioRequest): Observable<Anio> {
    return this.http.post<Anio>(`${API}/anios`, body);
  }
  actualizarAnio(id: number, body: AnioRequest): Observable<Anio> {
    return this.http.put<Anio>(`${API}/anios/${id}`, body);
  }
  activarAnio(id: number): Observable<Anio> {
    return this.http.patch<Anio>(`${API}/anios/${id}/activar`, {});
  }
  cerrarAnio(id: number): Observable<Anio> {
    return this.http.patch<Anio>(`${API}/anios/${id}/cerrar`, {});
  }

  // Periodos
  periodos(anioId: number): Observable<Periodo[]> {
    return this.http.get<Periodo[]>(`${API}/periodos`, { params: new HttpParams().set('anioId', anioId) });
  }
  crearPeriodo(body: PeriodoRequest): Observable<Periodo> {
    return this.http.post<Periodo>(`${API}/periodos`, body);
  }
  actualizarPeriodo(id: number, body: PeriodoRequest): Observable<Periodo> {
    return this.http.put<Periodo>(`${API}/periodos/${id}`, body);
  }
  cambiarEstadoPeriodo(id: number, estado: EstadoPlan): Observable<Periodo> {
    return this.http.patch<Periodo>(`${API}/periodos/${id}/estado`, { estado });
  }
  eliminarPeriodo(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/periodos/${id}`);
  }

  // Niveles y grados (catálogo de solo lectura)
  niveles(): Observable<Nivel[]> {
    return this.http.get<Nivel[]>(`${API}/niveles`);
  }
  grados(nivelId?: number): Observable<Grado[]> {
    let params = new HttpParams();
    if (nivelId) params = params.set('nivelId', nivelId);
    return this.http.get<Grado[]>(`${API}/grados`, { params });
  }

  // Secciones
  secciones(anioId?: number, gradoId?: number): Observable<Seccion[]> {
    let params = new HttpParams();
    if (anioId) params = params.set('anioId', anioId);
    if (gradoId) params = params.set('gradoId', gradoId);
    return this.http.get<Seccion[]>(`${API}/secciones`, { params });
  }
  crearSeccion(body: SeccionRequest): Observable<Seccion> {
    return this.http.post<Seccion>(`${API}/secciones`, body);
  }
  actualizarSeccion(id: number, body: SeccionRequest): Observable<Seccion> {
    return this.http.put<Seccion>(`${API}/secciones/${id}`, body);
  }
  eliminarSeccion(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/secciones/${id}`);
  }

  // Aulas
  aulas(tipo?: TipoAula): Observable<Aula[]> {
    let params = new HttpParams();
    if (tipo) params = params.set('tipo', tipo);
    return this.http.get<Aula[]>(`${API}/aulas`, { params });
  }
  crearAula(body: AulaRequest): Observable<Aula> {
    return this.http.post<Aula>(`${API}/aulas`, body);
  }
  actualizarAula(id: number, body: AulaRequest): Observable<Aula> {
    return this.http.put<Aula>(`${API}/aulas/${id}`, body);
  }
  eliminarAula(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/aulas/${id}`);
  }

  // Cursos
  cursos(): Observable<Curso[]> {
    return this.http.get<Curso[]>(`${API}/cursos`);
  }
  crearCurso(body: CursoRequest): Observable<Curso> {
    return this.http.post<Curso>(`${API}/cursos`, body);
  }
  actualizarCurso(id: number, body: CursoRequest): Observable<Curso> {
    return this.http.put<Curso>(`${API}/cursos/${id}`, body);
  }
  eliminarCurso(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/cursos/${id}`);
  }

  // Docentes (paginado; planificacion-ms devuelve `total` en vez de `totalElementos`)
  docentes(q = '', page = 0, size = 20): Observable<Page<Docente>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    return this.http
      .get<{ contenido: Docente[]; pagina: number; tamano: number; total: number; totalPaginas: number }>(`${API}/docentes`, { params })
      .pipe(map(p => ({ contenido: p.contenido, pagina: p.pagina, tamano: p.tamano, totalElementos: p.total, totalPaginas: p.totalPaginas })));
  }
  crearDocente(body: DocenteRequest): Observable<Docente> {
    return this.http.post<Docente>(`${API}/docentes`, body);
  }
  actualizarDocente(id: number, body: DocenteRequest): Observable<Docente> {
    return this.http.put<Docente>(`${API}/docentes/${id}`, body);
  }
  vincularUsuarioDocente(id: number, usuarioId: number): Observable<Docente> {
    return this.http.patch<Docente>(`${API}/docentes/${id}/usuario`, { usuarioId });
  }
  eliminarDocente(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/docentes/${id}`);
  }

  // Malla curricular
  malla(gradoId: number): Observable<MallaGrado> {
    return this.http.get<MallaGrado>(`${API}/malla/grado/${gradoId}`);
  }
  agregarCursoMalla(gradoId: number, cursoId: number, horasSemanales: number): Observable<MallaItem> {
    return this.http.post<MallaItem>(`${API}/malla`, { gradoId, cursoId, horasSemanales });
  }
  cambiarHorasMalla(id: number, horasSemanales: number): Observable<MallaItem> {
    return this.http.put<MallaItem>(`${API}/malla/${id}`, { horasSemanales });
  }
  quitarCursoMalla(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/malla/${id}`);
  }

  // Asignaciones docente-curso-sección
  asignaciones(seccionId?: number, docenteId?: number): Observable<Asignacion[]> {
    let params = new HttpParams();
    if (seccionId) params = params.set('seccionId', seccionId);
    if (docenteId) params = params.set('docenteId', docenteId);
    return this.http.get<Asignacion[]>(`${API}/asignaciones`, { params });
  }
  crearAsignacion(body: AsignacionRequest): Observable<Asignacion> {
    return this.http.post<Asignacion>(`${API}/asignaciones`, body);
  }
  cambiarDocenteAsignacion(id: number, docenteId: number): Observable<Asignacion> {
    return this.http.patch<Asignacion>(`${API}/asignaciones/${id}/docente`, { docenteId });
  }
  eliminarAsignacion(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/asignaciones/${id}`);
  }

  // Bloques horarios
  bloques(turno?: string): Observable<Bloque[]> {
    let params = new HttpParams();
    if (turno) params = params.set('turno', turno);
    return this.http.get<Bloque[]>(`${API}/bloques`, { params });
  }
  crearBloque(body: BloqueRequest): Observable<Bloque> {
    return this.http.post<Bloque>(`${API}/bloques`, body);
  }
  actualizarBloque(id: number, body: BloqueRequest): Observable<Bloque> {
    return this.http.put<Bloque>(`${API}/bloques/${id}`, body);
  }
  eliminarBloque(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/bloques/${id}`);
  }

  // Horarios
  horarioSeccion(seccionId: number): Observable<HorarioSeccion> {
    return this.http.get<HorarioSeccion>(`${API}/horarios/seccion/${seccionId}`);
  }
  /** Todas las clases del año (para saber qué aulas están ocupadas en cada bloque). */
  horariosAnio(anioId: number): Observable<ClaseHorario[]> {
    return this.http.get<ClaseHorario[]>(`${API}/horarios/anio/${anioId}`);
  }
  horarioDocente(docenteId: number): Observable<HorarioDocente> {
    return this.http.get<HorarioDocente>(`${API}/horarios/docente/${docenteId}`);
  }
  docenteMio(): Observable<DocenteMio> {
    return this.http.get<DocenteMio>(`${API}/docentes/mi`);
  }
  agregarClase(body: HorarioRequest): Observable<ClaseHorario> {
    return this.http.post<ClaseHorario>(`${API}/horarios`, body);
  }
  actualizarClase(id: number, body: HorarioRequest): Observable<ClaseHorario> {
    return this.http.put<ClaseHorario>(`${API}/horarios/${id}`, body);
  }
  quitarClase(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/horarios/${id}`);
  }
  /** Estado de cada sección del año antes de armar el horario automático. */
  preparacionHorarios(anioId: number): Observable<PreparacionSeccion[]> {
    return this.http.get<PreparacionSeccion[]>(`${API}/horarios/preparacion`, { params: new HttpParams().set('anioId', anioId) });
  }
  generarHorarios(body: GenerarHorarioRequest): Observable<ResultadoGeneracion> {
    return this.http.post<ResultadoGeneracion>(`${API}/horarios/generar`, body);
  }
  agregarMallaLote(body: MallaLoteRequest): Observable<MallaLoteResultado> {
    return this.http.post<MallaLoteResultado>(`${API}/malla/lote`, body);
  }
  validarHorario(seccionId: number): Observable<ValidacionHorario> {
    return this.http.get<ValidacionHorario>(`${API}/horarios/validar/${seccionId}`);
  }
  publicarHorario(seccionId: number): Observable<Publicacion> {
    return this.http.post<Publicacion>(`${API}/horarios/publicar/seccion/${seccionId}`, {});
  }
  publicaciones(seccionId: number): Observable<Publicacion[]> {
    return this.http.get<Publicacion[]>(`${API}/horarios/publicar/seccion/${seccionId}`);
  }

  // Feriados
  feriados(anioId?: number): Observable<Feriado[]> {
    let params = new HttpParams();
    if (anioId) params = params.set('anioId', anioId);
    return this.http.get<Feriado[]>(`${API}/feriados`, { params });
  }
  crearFeriado(body: FeriadoRequest): Observable<Feriado> {
    return this.http.post<Feriado>(`${API}/feriados`, body);
  }
  actualizarFeriado(id: number, body: FeriadoRequest): Observable<Feriado> {
    return this.http.put<Feriado>(`${API}/feriados/${id}`, body);
  }
  eliminarFeriado(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/feriados/${id}`);
  }
}
