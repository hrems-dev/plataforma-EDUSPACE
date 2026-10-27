import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AsistenciaHijo } from '../../../core/models/asistencia.model';
import { Cuota } from '../../../core/models/cobro.model';
import { ExamenDisponible } from '../../../core/models/examen.model';
import { ClaseHorario, DIAS_SEMANA } from '../../../core/models/planificacion.model';
import { TareaPendiente } from '../../../core/models/tarea.model';
import { AsistenciaService } from '../../../core/services/asistencia.service';
import { CobroService } from '../../../core/services/cobro.service';
import { ExamenService } from '../../../core/services/examen.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { TareaService } from '../../../core/services/tarea.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { SelectorHijoComponent } from '../../../shared/components/selector-hijo/selector-hijo.component';

@Component({
  selector: 'app-familia-inicio',
  imports: [RouterLink, DatePipe, DecimalPipe, PageHeaderComponent, SelectorHijoComponent],
  templateUrl: './familia-inicio.component.html'
})
export class FamiliaInicioComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  private readonly asistenciaApi = inject(AsistenciaService);
  private readonly tareasApi = inject(TareaService);
  private readonly examenesApi = inject(ExamenService);
  private readonly cobroApi = inject(CobroService);
  private readonly plan = inject(PlanificacionService);

  protected readonly error = signal<string | null>(null);
  protected readonly asistencia = signal<AsistenciaHijo[]>([]);
  protected readonly tareas = signal<TareaPendiente[]>([]);
  protected readonly examenes = signal<ExamenDisponible[]>([]);
  protected readonly cuotas = signal<Cuota[]>([]);
  protected readonly clases = signal<ClaseHorario[]>([]);

  protected readonly hoy = new Date().getDay();
  protected readonly nombreDia = DIAS_SEMANA[this.hoy] ?? 'Fin de semana';

  protected readonly asistenciaHijo = computed(() => this.asistencia().find(a => a.alumnoId === this.ctx.seleccionado()?.alumnoId) ?? null);
  protected readonly tareasHijo = computed(() => this.tareas().filter(t => t.alumnoId === this.ctx.seleccionado()?.alumnoId && t.estadoEntrega === 'SIN_ENTREGAR'));
  protected readonly cuotasHijo = computed(() =>
    this.cuotas()
      .filter(c => c.alumnoId === this.ctx.seleccionado()?.alumnoId && ['PENDIENTE', 'VENCIDA', 'PARCIAL'].includes(c.estado))
      .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento))
  );
  protected readonly vencidas = computed(() => this.cuotasHijo().filter(c => c.estado === 'VENCIDA' || c.diasAtraso > 0));
  protected readonly clasesHoy = computed(() => this.clases().filter(c => c.diaSemana === this.hoy).sort((a, b) => a.bloqueOrden - b.bloqueOrden));

  constructor() {
    // El horario es de la sección del hijo elegido: se recarga cuando cambia la matrícula seleccionada.
    effect(() => {
      this.ctx.matriculaActual();
      untracked(() => this.cargarHorario());
    });
  }

  ngOnInit(): void {
    this.ctx.cargar().subscribe({
      next: () => {
        this.asistenciaApi.mias().subscribe({ next: a => this.asistencia.set(a), error: () => this.asistencia.set([]) });
        this.tareasApi.mias().subscribe({ next: t => this.tareas.set(t), error: () => this.tareas.set([]) });
        // Rendir exámenes es del alumno; el apoderado ve sus resultados en la pantalla de exámenes.
        if (!this.ctx.esApoderado()) this.examenesApi.disponibles().subscribe({ next: e => this.examenes.set(e), error: () => this.examenes.set([]) });
        if (this.ctx.esApoderado()) this.cobroApi.misCuotas().subscribe({ next: c => this.cuotas.set(c), error: () => this.cuotas.set([]) });
      },
      error: e => this.error.set(mensajeError(e, 'No se pudo cargar la información de tus hijos.'))
    });
  }

  private cargarHorario(): void {
    const m = this.ctx.matriculaActual();
    if (!m) return;
    this.plan.horarioSeccion(m.seccionId).subscribe({ next: h => this.clases.set(h.clases), error: () => this.clases.set([]) });
  }

  protected hora(h: string): string {
    return h.slice(0, 5);
  }

  protected total(cs: Cuota[]): number {
    return cs.reduce((s, c) => s + c.saldo, 0);
  }
}
