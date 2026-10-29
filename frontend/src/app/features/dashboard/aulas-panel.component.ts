import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ReporteSeccion } from '../../core/models/asistencia.model';
import { AlumnoSeccion, Cupo } from '../../core/models/matricula.model';
import { Anio, Asignacion, Aula, Docente, MallaGrado, Seccion } from '../../core/models/planificacion.model';
import { AsistenciaService } from '../../core/services/asistencia.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { MatriculaService } from '../../core/services/matricula.service';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { mensajeError } from '../../core/utils/errores';
import { AsignarDocenteComponent } from './asignar-docente.component';
import { MatricularEnSeccionComponent } from './matricular-en-seccion.component';
import { SeccionFormComponent } from './seccion-form.component';
import { TrasladarAlumnoComponent } from './trasladar-alumno.component';

const seguro = <T>(obs: Observable<T>): Observable<T | null> => obs.pipe(catchError(() => of(null)));

interface Detalle {
  seccion: Seccion;
  alumnos: AlumnoSeccion[] | null;
  reporte: ReporteSeccion | null;
  asignaciones: Asignacion[] | null;
  malla: MallaGrado | null;
  clases: number | null;
  aula: Aula | null;
}

/** Aulas del año en una lista vertical; al elegir una se ve su ficha: tutor, alumnos, faltas, cursos y docentes. */
@Component({
  selector: 'app-aulas-panel',
  imports: [RouterLink, AsignarDocenteComponent, MatricularEnSeccionComponent, SeccionFormComponent, TrasladarAlumnoComponent],
  template: `
    <section class="card" aria-labelledby="t-aulas">
      <div class="card-body">
        <div class="sec-cab">
          <h2 id="t-aulas" class="sec-titulo">Aulas y secciones</h2>
          <span class="muted">{{ secciones().length }} sección(es) · elige una para ver su información</span>
          @if (esAdmin() && anio()) { <button type="button" class="btn btn-sm btn-primary" style="margin-left: auto" (click)="formSeccion.set({ editar: null })">Nueva sección</button> }
        </div>

        <div class="aulas-layout">
          <!-- Lista vertical -->
          <div class="aulas-col">
            <div class="chips" role="group" aria-label="Filtrar por nivel">
              <button type="button" class="chip" [class.on]="nivelFiltro() === 0" (click)="nivelFiltro.set(0)">Todas</button>
              @for (n of niveles(); track n.id) {
                <button type="button" class="chip" [class.on]="nivelFiltro() === n.id" (click)="nivelFiltro.set(n.id)">{{ n.nombre }}</button>
              }
            </div>
            <ul class="aula-lista">
              @for (s of visibles(); track s.id) {
                <li>
                  <button type="button" class="aula-item" [class.activa]="s.id === elegida()" [attr.aria-current]="s.id === elegida() ? 'true' : null" (click)="elegir(s.id)">
                    <span class="aula-fila">
                      <strong>{{ s.gradoNombre }} · {{ s.nombre }}</strong>
                      <span class="muted">{{ ocupados(s.id) }}/{{ s.capacidad }}</span>
                    </span>
                    <span class="aula-sub muted">{{ s.aulaNombre ?? 'Sin aula' }} · {{ s.tutorNombre ?? 'Sin tutor' }}</span>
                    <span [class]="'barra-cupo ' + nivelOcupacion(s)" role="img" [attr.aria-label]="porcentaje(s) + ' por ciento ocupado'"><span [style.width.%]="porcentaje(s)"></span></span>
                  </button>
                </li>
              } @empty {
                <li class="muted">No hay secciones para mostrar.</li>
              }
            </ul>
          </div>

          <!-- Ficha del aula elegida -->
          <div class="aula-ficha" aria-live="polite">
            @if (!elegida()) {
              <div class="vacio">
                <strong>Elige un aula de la lista</strong>
                <span class="muted">Verás su tutor, cuántos alumnos tiene, quiénes tienen faltas y qué docentes dictan cada curso.</span>
              </div>
            } @else if (cargando()) {
              <p class="muted">Cargando la información del aula…</p>
            } @else if (detalle(); as d) {
              <header class="ficha-cab">
                <div>
                  <h3>{{ d.seccion.gradoNombre }} · Sección {{ d.seccion.nombre }}</h3>
                  <span class="muted">Año {{ d.seccion.anioNombre }} · turno {{ turno(d.seccion.turno) }}</span>
                </div>
                <span [class]="'badge ' + (d.seccion.activo ? 'badge-ok' : 'badge-gray')">{{ d.seccion.activo ? 'Activa' : 'Inactiva' }}</span>
              </header>

              <dl class="datos">
                <div><dt>Aula</dt><dd>{{ d.seccion.aulaNombre ?? 'Sin aula' }}@if (d.aula) { <span class="muted"> · admite {{ d.aula.capacidad }}</span> }</dd></div>
                <div>
                  <dt>Tutor</dt>
                  <dd>
                    @if (editandoTutor()) {
                      <select class="input" aria-label="Docente tutor" (change)="tutorElegido.set(num($event))">
                        <option value="0" [selected]="tutorElegido() === 0">Sin tutor</option>
                        @for (t of docentes(); track t.id) { <option [value]="t.id" [selected]="t.id === tutorElegido()">{{ t.apellidos }}, {{ t.nombres }}</option> }
                      </select>
                      <button type="button" class="btn btn-sm btn-primary" (click)="guardarTutor(d.seccion)">Guardar</button>
                      <button type="button" class="btn btn-sm" (click)="editandoTutor.set(false)">Cancelar</button>
                    } @else {
                      {{ d.seccion.tutorNombre ?? 'Sin tutor' }}
                      @if (esAdmin()) { <button type="button" class="enlace" (click)="cambiarTutor(d.seccion)">{{ d.seccion.tutorNombre ? 'Cambiar' : 'Asignar' }}</button> }
                    }
                  </dd>
                </div>
                <div><dt>Capacidad</dt><dd>{{ d.seccion.capacidad }} alumnos</dd></div>
                <div><dt>Matriculados</dt><dd>{{ matriculados(d) }}</dd></div>
                <div><dt>En pre-matrícula</dt><dd>{{ preMatriculados(d) }}</dd></div>
                <div><dt>Vacantes</dt><dd [class.malo]="vacantes(d) === 0">{{ vacantes(d) }}</dd></div>
                <div><dt>Cursos con docente</dt><dd>{{ d.asignaciones?.length ?? 0 }} de {{ d.malla?.cursos?.length ?? 0 }}</dd></div>
                <div><dt>Clases en el horario</dt><dd>{{ d.clases ?? '—' }}</dd></div>
              </dl>

              <h4 class="ficha-sub">Asistencia</h4>
              @if (!d.reporte || sesiones(d) === 0) {
                <p class="muted">Aún no hay asistencia registrada para esta sección.</p>
              } @else {
                <div class="mini-stats">
                  <div class="mini"><span class="mini-v">{{ promedio(d) }} %</span><span class="muted">asistencia promedio</span></div>
                  <div class="mini"><span class="mini-v">{{ conFaltas(d).length }}</span><span class="muted">alumno(s) con faltas</span></div>
                  <div class="mini" [class.alerta]="d.reporte.alertasExcesoFaltas.length > 0"><span class="mini-v">{{ d.reporte.alertasExcesoFaltas.length }}</span><span class="muted">en alerta por exceso</span></div>
                  <div class="mini"><span class="mini-v">{{ sesiones(d) }}</span><span class="muted">días registrados</span></div>
                </div>
                @if (conFaltas(d).length > 0) {
                  <div class="table-wrap">
                    <table class="table">
                      <thead><tr><th>Alumno con más faltas</th><th>Faltas</th><th>Tardanzas</th><th>Asistencia</th></tr></thead>
                      <tbody>
                        @for (f of conFaltas(d).slice(0, 6); track f.alumnoId) {
                          <tr>
                            <td>{{ f.alumnoNombre }} @if (f.excesoFaltas) { <span class="badge badge-danger">Exceso</span> }</td>
                            <td>{{ f.dias.faltas }}</td>
                            <td>{{ f.dias.tardanzas }}</td>
                            <td>{{ f.dias.porcentajeAsistencia }} %</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
              }

              <div class="sec-cab" style="margin-top: 18px">
                <h4 class="ficha-sub" style="margin: 0">Cursos y docentes</h4>
                <button type="button" class="btn btn-sm btn-primary" (click)="asignar.set({ editar: null })">Asignar docente</button>
              </div>
              @if ((d.asignaciones?.length ?? 0) === 0) {
                <p class="muted">Esta sección todavía no tiene docentes asignados.</p>
              } @else {
                <div class="table-wrap">
                  <table class="table">
                    <thead><tr><th>Curso</th><th>Docente</th><th></th></tr></thead>
                    <tbody>
                      @for (a of d.asignaciones; track a.id) {
                        <tr>
                          <td>{{ a.cursoNombre }}</td>
                          <td>{{ a.docenteNombre }}</td>
                          <td class="acciones">
                            <button type="button" class="btn btn-sm" (click)="asignar.set({ editar: a })">Cambiar</button>
                            <button type="button" class="btn btn-sm" (click)="quitar(a)">Quitar</button>
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
              @if (faltanCursos(d).length > 0) {
                <p class="aviso aviso-warn" style="margin-top: 10px">Sin docente: {{ faltanCursos(d).join(', ') }}.</p>
              }

              <div class="sec-cab" style="margin-top: 18px">
                <h4 class="ficha-sub" style="margin: 0">Alumnos de la sección ({{ d.alumnos?.length ?? 0 }})</h4>
                <button type="button" class="btn btn-sm btn-primary" [disabled]="vacantes(d) === 0" [title]="vacantes(d) === 0 ? 'La sección no tiene vacantes' : ''" (click)="matricular.set(d.seccion)">Matricular alumno</button>
              </div>
              <ul class="lista-alumnos abierta">
                @for (a of d.alumnos; track a.matriculaId) {
                  <li>
                    <a [routerLink]="['/alumnos', a.alumnoId]">{{ a.alumnoNombre }}</a>
                    <span [class]="'badge ' + (a.estado === 'MATRICULADO' ? 'badge-ok' : 'badge-warn')">{{ a.estado === 'MATRICULADO' ? 'Matriculado' : 'Pre-matrícula' }}</span>
                    <button type="button" class="enlace" style="margin-left: auto" (click)="trasladar.set(a)">Trasladar</button>
                  </li>
                } @empty {
                  <li class="muted">Aún no hay alumnos en esta sección.</li>
                }
              </ul>

              <div class="ficha-links">
                @if (esAdmin()) { <button type="button" class="btn btn-sm" (click)="formSeccion.set({ editar: d.seccion })">Editar sección</button> }
                <a class="btn btn-sm" routerLink="/planificacion/horarios" [queryParams]="{ seccion: d.seccion.id }">Ver horario</a>
                <a class="btn btn-sm" routerLink="/planificacion/horarios-auto">Armar horario</a>
                <a class="btn btn-sm" routerLink="/asistencia/reporte">Reporte de asistencia</a>
              </div>
            } @else {
              <p class="aviso aviso-danger" role="alert">No se pudo cargar la información de esta aula.</p>
            }
          </div>
        </div>
      </div>
    </section>

    @if (matricular(); as sec) {
      <app-matricular-en-seccion [seccion]="sec" (cerrar)="matricular.set(null)" (guardado)="matricular.set(null); cambio.emit()" />
    }
    @if (trasladar(); as al) {
      @if (detalle(); as d) {
        <app-trasladar-alumno [alumno]="al" [origen]="d.seccion" [secciones]="secciones()" (cerrar)="trasladar.set(null)" (guardado)="trasladar.set(null); cambio.emit()" />
      }
    }
    @if (formSeccion(); as f) {
      <app-seccion-form [anio]="anio()!" [editar]="f.editar" (cerrar)="formSeccion.set(null)" (guardado)="formSeccion.set(null); elegida.set(f.editar ? elegida() : null); cambio.emit()" />
    }
    @if (asignar(); as a) {
      <app-asignar-docente [secciones]="secciones()" [docentes]="docentes()" [seccionId]="elegida() ?? 0" [editar]="a.editar"
                           (cerrar)="asignar.set(null)" (guardado)="alGuardar()" />
    }
  `
})
export class AulasPanelComponent {
  private readonly plan = inject(PlanificacionService);
  private readonly matriculas = inject(MatriculaService);
  private readonly asistencia = inject(AsistenciaService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);

  readonly secciones = input.required<Seccion[]>();
  readonly niveles = input.required<{ id: number; nombre: string }[]>();
  readonly cupos = input<Cupo[] | null>(null);
  readonly docentes = input.required<Docente[]>();
  readonly esAdmin = input(false);
  /** Año de las secciones (hace falta para crear una sección nueva). */
  readonly anio = input<Anio | null>(null);
  /** Avisa al inicio que cambió algo (tutor, asignaciones) para que refresque sus totales. */
  readonly cambio = output<void>();

  protected readonly nivelFiltro = signal(0);
  protected readonly elegida = signal<number | null>(null);
  protected readonly detalle = signal<Detalle | null>(null);
  protected readonly cargando = signal(false);
  protected readonly matricular = signal<Seccion | null>(null);
  protected readonly trasladar = signal<AlumnoSeccion | null>(null);
  protected readonly formSeccion = signal<{ editar: Seccion | null } | null>(null);
  protected readonly asignar = signal<{ editar: Asignacion | null } | null>(null);
  protected readonly editandoTutor = signal(false);
  protected readonly tutorElegido = signal(0);
  private aulas: Aula[] | null = null;

  protected readonly visibles = computed(() => {
    const n = this.nivelFiltro();
    return this.secciones().filter(s => !n || s.nivelId === n);
  });

  constructor() {
    // Cuando el inicio vuelve a leer las secciones (tras cambiar tutor o asignaciones) la ficha abierta se rearma con lo nuevo.
    effect(() => {
      const secs = this.secciones();
      const id = untracked(this.elegida);
      if (id !== null && secs.some(s => s.id === id)) untracked(() => this.cargarDetalle(id));
    });
    // Si se cambia de nivel y el aula elegida ya no está en la lista, se cierra su ficha.
    effect(() => {
      const id = this.elegida();
      if (id !== null && !this.visibles().some(s => s.id === id)) {
        this.elegida.set(null);
        this.detalle.set(null);
      }
    });
  }

  protected elegir(id: number): void {
    if (this.elegida() === id) return;
    this.elegida.set(id);
    this.editandoTutor.set(false);
    this.cargarDetalle(id);
  }

  private cargarDetalle(id: number): void {
    const seccion = this.secciones().find(s => s.id === id);
    if (!seccion) return;
    if (this.detalle()?.seccion.id !== id) this.cargando.set(true); // al refrescar la misma aula no parpadea
    forkJoin({
      alumnos: seguro(this.matriculas.alumnosDeSeccion(id)),
      reporte: seguro(this.asistencia.reporteSeccion(id)),
      asignaciones: seguro(this.plan.asignaciones(id)),
      malla: seguro(this.plan.malla(seccion.gradoId)),
      horario: seguro(this.plan.horarioSeccion(id)),
      aulas: this.aulas ? of(this.aulas) : seguro(this.plan.aulas())
    }).subscribe(r => {
      if (this.elegida() !== id) return; // el usuario ya eligió otra aula
      this.aulas = r.aulas;
      this.detalle.set({
        seccion,
        alumnos: r.alumnos,
        reporte: r.reporte,
        asignaciones: r.asignaciones,
        malla: r.malla,
        clases: r.horario ? r.horario.clases.length : null,
        aula: r.aulas?.find(a => a.id === seccion.aulaId) ?? null
      });
      this.cargando.set(false);
    });
  }

  protected alGuardar(): void {
    this.asignar.set(null);
    this.cambio.emit(); // el inicio relee las secciones y la ficha se rearma sola
  }

  protected async quitar(a: Asignacion): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Quitar a ${a.docenteNombre} de ${a.cursoNombre}? También se quitan sus clases del horario.`, 'Quitar', true))) return;
    this.plan.eliminarAsignacion(a.id).subscribe({
      next: () => {
        this.avisos.success('Asignación eliminada.');
        this.alGuardar();
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo quitar la asignación.'))
    });
  }

  protected cambiarTutor(s: Seccion): void {
    this.tutorElegido.set(s.tutorDocenteId ?? 0);
    this.editandoTutor.set(true);
  }

  protected guardarTutor(s: Seccion): void {
    const tutor = this.tutorElegido();
    this.plan
      .actualizarSeccion(s.id, {
        anioId: s.anioId, gradoId: s.gradoId, aulaId: s.aulaId ?? null, nombre: s.nombre, turno: s.turno,
        capacidad: s.capacidad, tutorDocenteId: tutor || null, activo: s.activo
      })
      .subscribe({
        next: () => {
          this.avisos.success(tutor ? 'Tutor asignado.' : 'Se quitó el tutor.');
          this.editandoTutor.set(false);
          this.cambio.emit();
        },
        error: e => this.avisos.error(mensajeError(e, 'No se pudo cambiar el tutor.'))
      });
  }

  // --- cifras de la lista y de la ficha ---
  protected ocupados(seccionId: number): number {
    return this.cupos()?.find(c => c.seccionId === seccionId)?.ocupados ?? 0;
  }

  protected porcentaje(s: Seccion): number {
    return s.capacidad ? Math.min(100, Math.round((this.ocupados(s.id) / s.capacidad) * 100)) : 0;
  }

  protected nivelOcupacion(s: Seccion): string {
    const p = this.porcentaje(s);
    return p >= 100 ? 'llena' : p >= 90 ? 'alta' : '';
  }

  protected matriculados(d: Detalle): number | string {
    return d.alumnos ? d.alumnos.filter(a => a.estado === 'MATRICULADO').length : '—';
  }

  protected preMatriculados(d: Detalle): number | string {
    return d.alumnos ? d.alumnos.filter(a => a.estado === 'PRE_MATRICULA').length : '—';
  }

  protected vacantes(d: Detalle): number {
    const ocupados = d.alumnos ? d.alumnos.filter(a => a.estado === 'MATRICULADO' || a.estado === 'PRE_MATRICULA').length : 0;
    return Math.max(0, d.seccion.capacidad - ocupados);
  }

  protected sesiones(d: Detalle): number {
    return Math.max(0, ...(d.reporte?.alumnos ?? []).map(a => a.dias.sesiones));
  }

  protected promedio(d: Detalle): number {
    const con = (d.reporte?.alumnos ?? []).filter(a => a.dias.sesiones > 0);
    return con.length ? Math.round(con.reduce((s, a) => s + a.dias.porcentajeAsistencia, 0) / con.length) : 0;
  }

  protected conFaltas(d: Detalle) {
    return (d.reporte?.alumnos ?? []).filter(a => a.dias.faltas > 0).sort((a, b) => b.dias.faltas - a.dias.faltas);
  }

  protected faltanCursos(d: Detalle): string[] {
    const con = new Set((d.asignaciones ?? []).map(a => a.cursoId));
    return (d.malla?.cursos ?? []).filter(c => !con.has(c.cursoId)).map(c => c.cursoNombre);
  }

  protected turno(t: string): string {
    return t === 'MANANA' ? 'mañana' : t === 'TARDE' ? 'tarde' : t.toLowerCase();
  }

  protected num(e: Event): number {
    return Number((e.target as HTMLSelectElement).value);
  }
}
