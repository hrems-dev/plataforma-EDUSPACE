import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AlumnoSeccion } from '../../../core/models/matricula.model';
import { DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { MatriculaService } from '../../../core/services/matricula.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/** Lista de cada sección que dicta el docente, con buscador y atajos a asistencia y notas. */
@Component({
  selector: 'app-docente-alumnos',
  imports: [RouterLink, PageHeaderComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Mis alumnos" subtitulo="Las secciones donde dictas clase y quiénes están matriculados." />
      @if (secciones().length === 0 && !cargando()) {
        <div class="aviso aviso-warn">Aún no tienes secciones asignadas. La secretaría las asigna en Planificación → Asignaciones.</div>
      }
      <div class="toolbar">
        <select class="input" aria-label="Sección" [value]="seccionId()" (change)="elegir(+$any($event.target).value)">
          @for (s of secciones(); track s.id) { <option [value]="s.id">{{ s.nombre }} · {{ s.cursos.join(', ') }}</option> }
        </select>
        <input class="input" type="search" placeholder="Buscar alumno…" aria-label="Buscar alumno" [value]="texto()" (input)="texto.set($any($event.target).value)" />
        <span class="muted">{{ visibles().length }} de {{ alumnos().length }}</span>
      </div>
      <div class="card"><div class="table-wrap">
        <table class="table">
          <thead><tr><th>Alumno</th><th>Código</th><th>Estado</th><th></th></tr></thead>
          <tbody>
            @for (a of visibles(); track a.alumnoId) {
              <tr>
                <td><strong>{{ a.alumnoNombre }}</strong></td>
                <td class="muted">{{ a.codigo }}</td>
                <td><span [class]="'badge ' + (a.estado === 'MATRICULADO' ? 'badge-ok' : 'badge-warn')">{{ a.estado === 'MATRICULADO' ? 'Matriculado' : 'Pre-matrícula' }}</span></td>
                <td style="text-align: right">
                  <a class="btn btn-sm" routerLink="/docente/asistencia">Asistencia</a>
                  <a class="btn btn-sm" routerLink="/docente/notas">Notas</a>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="4" class="vacio">{{ cargando() ? 'Cargando…' : 'No hay alumnos que coincidan.' }}</td></tr>
            }
          </tbody>
        </table>
      </div></div>
    </div>
  `
})
export class DocenteAlumnosComponent implements OnInit {
  private readonly contexto = inject(DocenteContextoService);
  private readonly api = inject(MatriculaService);
  private readonly avisos = inject(NotificationService);

  protected readonly secciones = signal<{ id: number; nombre: string; cursos: string[] }[]>([]);
  protected readonly seccionId = signal(0);
  protected readonly alumnos = signal<AlumnoSeccion[]>([]);
  protected readonly texto = signal('');
  protected readonly cargando = signal(true);
  protected readonly visibles = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return this.alumnos().filter(a => !t || `${a.alumnoNombre} ${a.codigo}`.toLowerCase().includes(t));
  });

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c => {
        const porSeccion = new Map<number, { id: number; nombre: string; cursos: string[] }>();
        for (const a of c.asignaciones) {
          const s = porSeccion.get(a.seccionId) ?? { id: a.seccionId, nombre: a.seccionNombre, cursos: [] };
          s.cursos.push(a.cursoNombre);
          porSeccion.set(a.seccionId, s);
        }
        this.secciones.set([...porSeccion.values()]);
        const primera = this.secciones()[0];
        if (primera) this.elegir(primera.id);
        else this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar tu información de docente.'));
      }
    });
  }

  protected elegir(id: number): void {
    this.seccionId.set(id);
    this.cargando.set(true);
    this.api.alumnosDeSeccion(id).subscribe({
      next: a => {
        this.alumnos.set([...a].sort((x, y) => x.alumnoNombre.localeCompare(y.alumnoNombre)));
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar la lista de alumnos.'));
      }
    });
  }
}
