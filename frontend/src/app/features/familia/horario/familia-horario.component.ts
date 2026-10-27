import { Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { ClaseHorario, DIAS_SEMANA } from '../../../core/models/planificacion.model';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { SelectorHijoComponent } from '../../../shared/components/selector-hijo/selector-hijo.component';

@Component({
  selector: 'app-familia-horario',
  imports: [PageHeaderComponent, SelectorHijoComponent],
  templateUrl: './familia-horario.component.html',
  styleUrl: '../../planificacion/horarios/horarios.component.css'
})
export class FamiliaHorarioComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);

  protected readonly dias = [1, 2, 3, 4, 5];
  protected readonly nombreDia = (d: number) => DIAS_SEMANA[d];
  protected readonly clases = signal<ClaseHorario[]>([]);
  protected readonly cargando = signal(true);
  protected readonly hoy = new Date().getDay();

  protected readonly filas = computed(() => {
    const m = new Map<number, { orden: number; inicio: string; fin: string }>();
    for (const c of this.clases()) m.set(c.bloqueOrden, { orden: c.bloqueOrden, inicio: c.horaInicio.slice(0, 5), fin: c.horaFin.slice(0, 5) });
    return [...m.values()].sort((a, b) => a.orden - b.orden);
  });

  constructor() {
    effect(() => {
      const m = this.ctx.matriculaActual();
      untracked(() => this.cargarHorario(m?.seccionId));
    });
  }

  ngOnInit(): void {
    this.ctx.cargar().subscribe({ error: e => this.avisos.error(mensajeError(e)) });
  }

  private cargarHorario(seccionId?: number): void {
    if (!seccionId) {
      this.clases.set([]);
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.plan.horarioSeccion(seccionId).subscribe({
      next: h => {
        this.clases.set(h.clases);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar el horario.'));
      }
    });
  }

  protected en(orden: number, dia: number): ClaseHorario[] {
    return this.clases().filter(c => c.bloqueOrden === orden && c.diaSemana === dia);
  }
}
