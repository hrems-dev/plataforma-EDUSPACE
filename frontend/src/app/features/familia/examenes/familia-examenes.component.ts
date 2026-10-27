import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ExamenDisponible, IntentoResultado } from '../../../core/models/examen.model';
import { ExamenService } from '../../../core/services/examen.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-familia-examenes',
  imports: [RouterLink, DatePipe, PageHeaderComponent],
  templateUrl: './familia-examenes.component.html'
})
export class FamiliaExamenesComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  private readonly api = inject(ExamenService);
  private readonly avisos = inject(NotificationService);

  protected readonly disponibles = signal<ExamenDisponible[]>([]);
  protected readonly mios = signal<IntentoResultado[]>([]);
  protected readonly cargando = signal(true);
  protected readonly esAlumno = computed(() => !this.ctx.esApoderado());

  ngOnInit(): void {
    this.ctx.cargar().subscribe({ error: e => this.avisos.error(mensajeError(e)) });
    this.api.mios().subscribe({
      next: r => {
        this.mios.set(r as IntentoResultado[]);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los resultados.'));
      }
    });
    if (!this.ctx.esApoderado()) this.api.disponibles().subscribe({ next: d => this.disponibles.set(d), error: () => this.disponibles.set([]) });
  }

  protected clase(e: string): string {
    return e === 'CALIFICADO' ? 'badge-ok' : e === 'ENVIADO' ? 'badge-warn' : e === 'EN_CURSO' ? 'badge' : 'badge-gray';
  }
}
