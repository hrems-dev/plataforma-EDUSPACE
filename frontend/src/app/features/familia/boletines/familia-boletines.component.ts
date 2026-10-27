import { DatePipe } from '@angular/common';
import { Component, OnInit, effect, inject, signal, untracked } from '@angular/core';
import { Boletin } from '../../../core/models/calificacion.model';
import { Periodo } from '../../../core/models/planificacion.model';
import { CalificacionService } from '../../../core/services/calificacion.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { SelectorHijoComponent } from '../../../shared/components/selector-hijo/selector-hijo.component';

/** Boletines oficiales del hijo seleccionado: los vigentes primero y descarga con un clic. */
@Component({
  selector: 'app-familia-boletines',
  imports: [DatePipe, PageHeaderComponent, SelectorHijoComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Boletines" subtitulo="Documentos oficiales de notas por periodo, listos para descargar." />
      <app-selector-hijo />
      @if (cargando()) {
        <div class="muted">Cargando…</div>
      } @else if (boletines().length === 0) {
        <div class="aviso">Aún no hay boletines publicados. Aparecerán aquí cuando el colegio cierre cada periodo.</div>
      } @else {
        <div class="card"><div class="table-wrap">
          <table class="table">
            <thead><tr><th>Periodo</th><th>Versión</th><th>Generado</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              @for (b of boletines(); track b.id) {
                <tr>
                  <td><strong>{{ periodo(b.periodoId) }}</strong></td>
                  <td>v{{ b.version }}</td>
                  <td>{{ b.generadoEn | date: 'd MMM y, HH:mm' }}</td>
                  <td><span [class]="'badge ' + (b.estado === 'VIGENTE' ? 'badge-ok' : 'badge-gray')">{{ b.estado === 'VIGENTE' ? 'Vigente' : 'Reemplazado' }}</span></td>
                  <td style="text-align: right">
                    @if (b.estado === 'VIGENTE') { <button type="button" class="btn btn-primary btn-sm" (click)="descargar(b)">Descargar PDF</button> }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div></div>
      }
    </div>
  `
})
export class FamiliaBoletinesComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  private readonly api = inject(CalificacionService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);

  protected readonly boletines = signal<Boletin[]>([]);
  protected readonly periodos = signal<Periodo[]>([]);
  protected readonly cargando = signal(true);

  ngOnInit(): void {
    this.plan.anioActivo().subscribe({ next: a => this.plan.periodos(a.id).subscribe(p => this.periodos.set(p)), error: () => undefined });
    this.ctx.cargar().subscribe({ error: e => this.avisos.error(mensajeError(e)) });
  }

  constructor() {
    // Al elegir otro hijo (o cuando termina de cargar el contexto) se piden sus boletines.
    effect(() => {
      this.ctx.seleccionado();
      untracked(() => this.cargar());
    });
  }

  private cargar(): void {
    const hijo = this.ctx.seleccionado();
    if (!hijo) {
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.api.boletines(hijo.alumnoId).subscribe({
      next: b => {
        // Vigentes primero, y dentro de cada grupo lo más reciente arriba.
        this.boletines.set([...b].sort((x, y) => Number(y.estado === 'VIGENTE') - Number(x.estado === 'VIGENTE') || y.generadoEn.localeCompare(x.generadoEn)));
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los boletines.'));
      }
    });
  }

  protected periodo(id: number): string {
    return this.periodos().find(p => p.id === id)?.nombre ?? `Periodo ${id}`;
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
