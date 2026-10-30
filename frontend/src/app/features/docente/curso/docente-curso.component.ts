import { Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AlumnoSeccion } from '../../../core/models/matricula.model';
import { Asignacion } from '../../../core/models/planificacion.model';
import { DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { MatriculaService } from '../../../core/services/matricula.service';
import { CursoCabeceraComponent } from '../../../shared/aula/curso-cabecera.component';
import { PestanaCurso, PestanasCursoComponent } from '../../../shared/aula/pestanas-curso.component';
import { MaterialesCursoComponent } from '../../../shared/aula/materiales-curso.component';
import { DocenteAsistenciaComponent } from '../asistencia/docente-asistencia.component';
import { DocenteExamenesComponent } from '../examenes/docente-examenes.component';
import { DocenteNotasComponent } from '../notas/docente-notas.component';
import { DocenteTareasComponent } from '../tareas/docente-tareas.component';

/** Un curso del docente en una sección: sus tareas (crear, publicar, calificar), sus alumnos y sus exámenes en una sola página. */
@Component({
  selector: 'app-docente-curso',
  imports: [RouterLink, CursoCabeceraComponent, PestanasCursoComponent, MaterialesCursoComponent, DocenteTareasComponent, DocenteAsistenciaComponent, DocenteNotasComponent, DocenteExamenesComponent],
  template: `
    @if (asignacion(); as a) {
      <app-curso-cabecera [cursoId]="a.cursoId" [nombre]="a.cursoNombre" volverA="/docente/inicio"
        [subtitulo]="a.seccionNombre + ' · ' + alumnosMatriculados() + ' alumnos'">
        <button type="button" class="btn" (click)="pestana.set('asistencia')">Pasar lista</button>
        <button type="button" class="btn" (click)="pestana.set('notas')">Registrar notas</button>
      </app-curso-cabecera>

      <app-pestanas-curso [pestanas]="pestanas()" [(activa)]="pestana" />

      @switch (pestana()) {
        @case ('materiales') { <app-materiales-curso [seccionId]="a.seccionId" [cursoId]="a.cursoId" [editable]="true" /> }
        @case ('tareas') { <app-docente-tareas [seccionId]="a.seccionId" [cursoId]="a.cursoId" [embebido]="true" /> }
        @case ('asistencia') { <app-docente-asistencia [seccionId]="a.seccionId" [cursoId]="a.cursoId" [embebido]="true" /> }
        @case ('notas') { <app-docente-notas [seccionId]="a.seccionId" [cursoId]="a.cursoId" [embebido]="true" /> }
        @case ('alumnos') {
          <section class="card"><div class="card-body">
            <input class="input" type="search" placeholder="Buscar alumno…" aria-label="Buscar alumno" [value]="texto()" (input)="texto.set($any($event.target).value)" style="margin-bottom: 12px" />
            <div class="alumnos">
              @for (al of visibles(); track al.alumnoId) {
                <div class="alumno">
                  <span class="av" aria-hidden="true">{{ ini(al.alumnoNombre) }}</span>
                  <span><strong>{{ al.alumnoNombre }}</strong><br /><small class="muted">{{ al.codigo }}</small></span>
                  <span [class]="'badge ' + (al.estado === 'MATRICULADO' ? 'badge-ok' : 'badge-warn')">{{ al.estado === 'MATRICULADO' ? 'Matriculado' : 'Pre-matrícula' }}</span>
                </div>
              } @empty { <p class="muted">No hay alumnos para mostrar.</p> }
            </div>
          </div></section>
        }
        @case ('examenes') { <app-docente-examenes [seccionId]="a.seccionId" [cursoId]="a.cursoId" [embebido]="true" /> }
      }
    } @else if (cargando()) {
      <p class="muted">Cargando el curso…</p>
    } @else {
      <div class="aviso aviso-warn">No encontramos este curso entre tus asignaciones. <a routerLink="/docente/inicio">Volver a Mis cursos</a></div>
    }
  `,
  styles: `
    .alumnos { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 10px; }
    .alumno { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 12px; }
    .alumno .badge { margin-left: auto; }
    .av { flex: none; width: 38px; height: 38px; border-radius: 50%; display: grid; place-items: center; background: var(--primary-soft); color: var(--primary); font-weight: 800; font-size: .8rem; }
    .fila { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid var(--line); }
    .fila:first-child { border-top: 0; }
  `
})
export class DocenteCursoComponent implements OnInit {
  private readonly contexto = inject(DocenteContextoService);
  private readonly matriculas = inject(MatriculaService);

  readonly seccionId = input.required<string>();
  readonly cursoId = input.required<string>();
  readonly ver = input<string | undefined>(undefined);

  protected readonly pestana = signal('tareas');
  protected readonly asignaciones = signal<Asignacion[]>([]);
  protected readonly alumnos = signal<AlumnoSeccion[]>([]);
  protected readonly texto = signal('');
  protected readonly cargando = signal(true);

  protected readonly asignacion = computed(() => this.asignaciones().find(a => a.seccionId === Number(this.seccionId()) && a.cursoId === Number(this.cursoId())) ?? null);
  protected readonly alumnosMatriculados = computed(() => this.alumnos().filter(a => a.estado === 'MATRICULADO').length);
  protected readonly visibles = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return this.alumnos().filter(a => !t || a.alumnoNombre.toLowerCase().includes(t));
  });
  protected readonly pestanas = computed<PestanaCurso[]>(() => [
    { id: 'tareas', etiqueta: 'Tareas' },
    { id: 'materiales', etiqueta: 'Materiales' },
    { id: 'asistencia', etiqueta: 'Asistencia' },
    { id: 'notas', etiqueta: 'Notas' },
    { id: 'examenes', etiqueta: 'Exámenes' },
    { id: 'alumnos', etiqueta: 'Alumnos' }
  ]);

  constructor() {
    effect(() => {
      const v = this.ver();
      if (v === 'tareas' || v === 'materiales' || v === 'asistencia' || v === 'notas' || v === 'alumnos' || v === 'examenes') untracked(() => this.pestana.set(v));
    });
  }

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c => {
        this.asignaciones.set(c.asignaciones);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false)
    });
    const sec = Number(this.seccionId());
    this.matriculas.alumnosDeSeccion(sec).subscribe({ next: l => this.alumnos.set(l), error: () => this.alumnos.set([]) });
  }

  protected ini(nombre: string): string {
    return (nombre.split(',')[1] ?? nombre).trim().charAt(0).toUpperCase();
  }
}
