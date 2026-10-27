import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ClaseHorario, DIAS_SEMANA } from '../../../core/models/planificacion.model';
import { Tarea } from '../../../core/models/tarea.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContextoDocente, DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { MatriculaService } from '../../../core/services/matricula.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { TareaService } from '../../../core/services/tarea.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

export interface SeccionCursoDetalle {
  alumnos: number;
  aula?: string;
  turno?: string;
}

@Component({
  selector: 'app-docente-inicio',
  imports: [RouterLink, PageHeaderComponent],
  templateUrl: './docente-inicio.component.html'
})
export class DocenteInicioComponent implements OnInit {
  private readonly contexto = inject(DocenteContextoService);
  private readonly plan = inject(PlanificacionService);
  private readonly matriculasApi = inject(MatriculaService);
  private readonly tareasApi = inject(TareaService);
  protected readonly usuario = inject(AuthService).currentUser;

  protected readonly ctx = signal<ContextoDocente | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly clasesHoy = signal<ClaseHorario[]>([]);
  protected readonly tareasPorCalificar = signal<Tarea[]>([]);
  protected readonly seccionInfo = signal<Record<number, SeccionCursoDetalle>>({});
  protected readonly cargando = signal(true);

  /** Lunes = 1 … viernes = 5 (igual que el horario); fines de semana no hay clases. */
  protected readonly diaHoy = new Date().getDay();
  /** El servicio devuelve «Apellidos, Nombres»: el saludo usa «Nombres Apellidos». */
  protected readonly saludo = computed(() => {
    const n = this.ctx()?.docente?.nombre;
    if (!n) return this.usuario()?.username ?? '';
    const [apellidos, nombres] = n.split(',').map(s => s.trim());
    return nombres ? `${nombres} ${apellidos}` : n;
  });
  protected readonly nombreDia = DIAS_SEMANA[this.diaHoy] ?? 'Fin de semana';

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c => {
        this.ctx.set(c);
        this.cargando.set(false);
        this.plan.horarioDocente(c.docente.id).subscribe({
          next: h => this.clasesHoy.set(h.clases.filter(x => x.diaSemana === this.diaHoy).sort((a, b) => a.bloqueOrden - b.bloqueOrden)),
          error: () => this.clasesHoy.set([])
        });
        this.tareasApi.listar({ estado: 'PUBLICADA' }, 0, 50).subscribe({
          next: p => this.tareasPorCalificar.set(p.contenido.filter(t => (t.entregadas ?? 0) > (t.calificadas ?? 0))),
          error: () => this.tareasPorCalificar.set([])
        });
        // Alumnos, aula y turno de cada sección que dicta. Los cupos son solo del personal (403 para docentes),
        // por eso se cuenta con la lista de alumnos de la sección, que sí pueden ver.
        const ids = [...new Set(c.asignaciones.map(a => a.seccionId))];
        if (ids.length === 0) return;
        forkJoin({
          secciones: this.plan.secciones(c.anio.id).pipe(catchError(() => of([]))),
          listas: forkJoin(ids.map(id => this.matriculasApi.alumnosDeSeccion(id).pipe(catchError(() => of(null)))))
        }).subscribe(({ secciones, listas }) => {
          const info: Record<number, SeccionCursoDetalle> = {};
          ids.forEach((id, i) => {
            const s = secciones.find(x => x.id === id);
            info[id] = { alumnos: listas[i]?.filter(a => a.estado === 'MATRICULADO').length ?? 0, aula: s?.aulaNombre, turno: s?.turno };
          });
          this.seccionInfo.set(info);
        });
      },
      error: e => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No se pudo cargar tu información de docente.'));
      }
    });
  }

  protected infoCurso(seccionId: number): SeccionCursoDetalle | null {
    return this.seccionInfo()[seccionId] ?? null;
  }

  protected hora(h: string): string {
    return h.slice(0, 5);
  }
}
