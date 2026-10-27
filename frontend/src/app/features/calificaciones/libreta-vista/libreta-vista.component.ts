import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Boletin, Libreta } from '../../../core/models/calificacion.model';
import { Periodo } from '../../../core/models/planificacion.model';
import { CalificacionService } from '../../../core/services/calificacion.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';

/** Libreta de un alumno: notas por curso y periodo, resultado anual y boletines. La usan secretaría y las familias. */
@Component({
  selector: 'app-libreta-vista',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './libreta-vista.component.html'
})
export class LibretaVistaComponent {
  private readonly plan = inject(PlanificacionService);
  private readonly api = inject(CalificacionService);
  private readonly avisos = inject(NotificationService);

  readonly libreta = input.required<Libreta>();
  protected readonly periodos = signal<Periodo[]>([]);

  protected readonly columnas = computed(() => {
    const ids = new Set<number>();
    for (const c of this.libreta().cursos) for (const k of Object.keys(c.notasPorPeriodo ?? {})) ids.add(Number(k));
    for (const p of this.periodos()) ids.add(p.id);
    return [...ids].sort((a, b) => a - b);
  });

  constructor() {
    effect(() => {
      const anio = this.libreta().anioId;
      this.plan.periodos(anio).subscribe({ next: p => this.periodos.set(p), error: () => this.periodos.set([]) });
    });
  }

  protected nombrePeriodo(id: number): string {
    return this.periodos().find(p => p.id === id)?.nombre ?? `Periodo ${id}`;
  }

  protected nota(c: Libreta['cursos'][number], periodoId: number): string {
    const n = c.notasPorPeriodo?.[String(periodoId)];
    return n ? `${n.promedio.toFixed(1)} (${n.literal})` : '—';
  }

  protected claseNota(c: Libreta['cursos'][number], periodoId: number): string {
    const n = c.notasPorPeriodo?.[String(periodoId)];
    return n && n.promedio < 11 ? 'var(--danger)' : '';
  }

  protected claseResultado(e?: string): string {
    return e === 'APROBADO' ? 'badge-ok' : e === 'RECUPERACION' ? 'badge-warn' : e ? 'badge-danger' : 'badge-gray';
  }

  protected descargar(b: Boletin): void {
    this.api.urlBoletin(b.id).subscribe({
      next: r => {
        const url = r.urlDescarga ?? r.url;
        if (url) window.open(url, '_blank', 'noopener,noreferrer');
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo abrir el boletín.'))
    });
  }
}
