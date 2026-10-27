import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ClaseHorario, DIAS_SEMANA } from '../../../core/models/planificacion.model';
import { DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

interface Fila {
  orden: number;
  inicio: string;
  fin: string;
}

@Component({
  selector: 'app-docente-horario',
  imports: [PageHeaderComponent],
  templateUrl: './docente-horario.component.html',
  styleUrl: '../../planificacion/horarios/horarios.component.css'
})
export class DocenteHorarioComponent implements OnInit {
  private readonly contexto = inject(DocenteContextoService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);

  protected readonly dias = [1, 2, 3, 4, 5];
  protected readonly nombreDia = (d: number) => DIAS_SEMANA[d];
  protected readonly clases = signal<ClaseHorario[]>([]);
  protected readonly cargando = signal(true);
  protected readonly hoy = new Date().getDay();

  protected readonly filas = computed<Fila[]>(() => {
    const m = new Map<number, Fila>();
    for (const c of this.clases()) m.set(c.bloqueOrden, { orden: c.bloqueOrden, inicio: c.horaInicio.slice(0, 5), fin: c.horaFin.slice(0, 5) });
    return [...m.values()].sort((a, b) => a.orden - b.orden);
  });

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c =>
        this.plan.horarioDocente(c.docente.id).subscribe({
          next: h => {
            this.clases.set(h.clases);
            this.cargando.set(false);
          },
          error: e => {
            this.cargando.set(false);
            this.avisos.error(mensajeError(e, 'No se pudo cargar tu horario.'));
          }
        }),
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar tu información de docente.'));
      }
    });
  }

  protected en(orden: number, dia: number): ClaseHorario[] {
    return this.clases().filter(c => c.bloqueOrden === orden && c.diaSemana === dia);
  }
}
