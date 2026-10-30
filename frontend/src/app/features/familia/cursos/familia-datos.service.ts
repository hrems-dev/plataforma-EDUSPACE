import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Libreta } from '../../../core/models/calificacion.model';
import { ClaseHorario, MallaItem } from '../../../core/models/planificacion.model';
import { TareaPendiente } from '../../../core/models/tarea.model';
import { CalificacionService } from '../../../core/services/calificacion.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { nombreNatural } from '../../../shared/aula/nombres';
import { TareaService } from '../../../core/services/tarea.service';

export interface CursoFamilia {
  cursoId: number;
  nombre: string;
  docente: string | null;
  horas: number;
  pendientes: number;
  proxima: TareaPendiente | null;
  promedio: number | null;
  literal: string | null;
}

/**
 * Los cursos del hijo elegido (o del propio alumno) con lo que importa de cada uno: quién lo dicta, tareas por entregar y nota.
 * Se comparte entre «Mis cursos» y la página de un curso, y se vuelve a pedir cuando cambia el hijo seleccionado.
 */
@Injectable({ providedIn: 'root' })
export class FamiliaDatosService {
  private readonly ctx = inject(FamiliaContextoService);
  private readonly plan = inject(PlanificacionService);
  private readonly tareasApi = inject(TareaService);
  private readonly notasApi = inject(CalificacionService);

  readonly malla = signal<MallaItem[]>([]);
  readonly clases = signal<ClaseHorario[]>([]);
  readonly tareas = signal<TareaPendiente[]>([]);
  readonly libretas = signal<Libreta[]>([]);
  readonly cargando = signal(true);

  private readonly delHijo = computed(() => this.tareas().filter(t => t.alumnoId === this.ctx.seleccionado()?.alumnoId));
  private readonly libreta = computed(() => this.libretas().find(l => l.alumnoId === this.ctx.seleccionado()?.alumnoId) ?? null);

  readonly cursos = computed<CursoFamilia[]>(() => {
    const docentes = new Map<number, string>();
    for (const c of this.clases()) if (!docentes.has(c.cursoId)) docentes.set(c.cursoId, c.docenteNombre);
    const notas = new Map((this.libreta()?.cursos ?? []).map(c => [c.cursoId, c]));
    // Los cursos salen de la malla del grado; si no hay malla se usan los del horario o los de la libreta.
    const base = new Map<number, { nombre: string; horas: number }>();
    for (const m of this.malla()) base.set(m.cursoId, { nombre: m.cursoNombre, horas: m.horasSemanales });
    for (const c of this.clases()) if (!base.has(c.cursoId)) base.set(c.cursoId, { nombre: c.cursoNombre, horas: 0 });
    for (const n of this.libreta()?.cursos ?? []) if (!base.has(n.cursoId)) base.set(n.cursoId, { nombre: n.cursoNombre, horas: 0 });
    return [...base].map(([cursoId, b]) => {
      const pend = this.delHijo().filter(t => t.cursoId === cursoId && t.estadoEntrega === 'SIN_ENTREGAR').sort((a, z) => a.fechaEntrega.localeCompare(z.fechaEntrega));
      const n = notas.get(cursoId);
      const ultimo = n ? Object.values(n.notasPorPeriodo).at(-1) : undefined;
      return {
        cursoId, nombre: b.nombre, docente: docentes.has(cursoId) ? 'Prof. ' + nombreNatural(docentes.get(cursoId)) : null, horas: b.horas, pendientes: pend.length, proxima: pend[0] ?? null,
        promedio: n?.promedioFinal ?? ultimo?.promedio ?? null, literal: n?.literalFinal ?? ultimo?.literal ?? null
      };
    }).sort((a, z) => a.nombre.localeCompare(z.nombre));
  });

  constructor() {
    effect(() => {
      const m = this.ctx.matriculaActual();
      untracked(() => this.cargar(m ? { gradoId: m.gradoId, seccionId: m.seccionId } : null));
    });
  }

  recargarTareas(): void {
    this.tareasApi.mias().pipe(catchError(() => of([] as TareaPendiente[]))).subscribe(t => this.tareas.set(t));
  }

  private cargar(m: { gradoId: number; seccionId: number } | null): void {
    if (!m) {
      this.cargando.set(!this.ctx.cargado());
      this.malla.set([]);
      this.clases.set([]);
      return;
    }
    this.cargando.set(true);
    forkJoin({
      malla: this.plan.malla(m.gradoId).pipe(catchError(() => of(null))),
      horario: this.plan.horarioSeccion(m.seccionId).pipe(catchError(() => of(null))),
      tareas: this.tareasApi.mias().pipe(catchError(() => of([] as TareaPendiente[]))),
      libretas: this.notasApi.mias().pipe(catchError(() => of([] as Libreta[])))
    }).subscribe(r => {
      this.malla.set(r.malla?.cursos ?? []);
      this.clases.set(r.horario?.clases ?? []);
      this.tareas.set(r.tareas);
      this.libretas.set(r.libretas);
      this.cargando.set(false);
    });
  }
}
