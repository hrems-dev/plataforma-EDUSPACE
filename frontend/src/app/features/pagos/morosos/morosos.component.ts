import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AlertaAdmin, Morosos } from '../../../core/models/pago.model';
import { NotificationService } from '../../../core/services/notification.service';
import { PagoConfigService } from '../../../core/services/pago-config.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const ETIQUETA_ALERTA: Record<string, string> = {
  SIN_TARIFA: 'Sin tarifa',
  CUOTA_PARCIAL: 'Cuota parcial',
  PERIODO_CERRADO: 'Periodo cerrado',
  OTRA: 'Otra'
};

@Component({
  selector: 'app-morosos',
  imports: [DatePipe, PageHeaderComponent],
  templateUrl: './morosos.component.html'
})
export class MorososComponent implements OnInit {
  private readonly api = inject(PagoConfigService);
  private readonly avisos = inject(NotificationService);

  protected readonly morosos = signal<Morosos | null>(null);
  protected readonly alertas = signal<AlertaAdmin[]>([]);
  protected readonly verResueltas = signal(false);
  protected readonly cargando = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  protected etiqueta(tipo: string): string {
    return ETIQUETA_ALERTA[tipo] ?? tipo;
  }

  protected soles(n: number): string {
    return 'S/ ' + n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.api.morosos().subscribe({
      next: m => {
        this.morosos.set(m);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar el reporte de morosidad.'));
      }
    });
    this.cargarAlertas();
  }

  protected alternarResueltas(): void {
    this.verResueltas.update(v => !v);
    this.cargarAlertas();
  }

  private cargarAlertas(): void {
    this.api.alertas(this.verResueltas()).subscribe({
      next: a => this.alertas.set(a),
      error: e => this.avisos.error(mensajeError(e, 'No se pudieron cargar las alertas.'))
    });
  }

  protected resolver(a: AlertaAdmin): void {
    this.api.resolverAlerta(a.id).subscribe({
      next: () => {
        this.avisos.success('Alerta resuelta.');
        this.cargarAlertas();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
