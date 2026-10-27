import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Libreta } from '../../../core/models/calificacion.model';
import { CalificacionService } from '../../../core/services/calificacion.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { SelectorHijoComponent } from '../../../shared/components/selector-hijo/selector-hijo.component';
import { LibretaVistaComponent } from '../../calificaciones/libreta-vista/libreta-vista.component';

@Component({
  selector: 'app-familia-notas',
  imports: [PageHeaderComponent, SelectorHijoComponent, LibretaVistaComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Notas y boletines" [subtitulo]="ctx.seleccionado()?.nombreCompleto ?? ''" />
      <app-selector-hijo />
      @if (libreta(); as l) {
        <app-libreta-vista [libreta]="l" />
      } @else if (cargando()) {
        <div class="muted">Cargando…</div>
      } @else {
        <div class="aviso aviso-warn">Todavía no hay notas para mostrar. Aparecen cuando los docentes las registran.</div>
      }
    </div>
  `
})
export class FamiliaNotasComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  private readonly api = inject(CalificacionService);
  private readonly avisos = inject(NotificationService);

  protected readonly libretas = signal<Libreta[]>([]);
  protected readonly cargando = signal(true);
  protected readonly libreta = computed(() => this.libretas().find(l => l.alumnoId === this.ctx.seleccionado()?.alumnoId) ?? null);

  ngOnInit(): void {
    this.ctx.cargar().subscribe({ error: e => this.avisos.error(mensajeError(e)) });
    this.api.mias().subscribe({
      next: l => {
        this.libretas.set(l);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las notas.'));
      }
    });
  }
}
