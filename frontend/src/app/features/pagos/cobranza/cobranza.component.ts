import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MEDIOS_PAGO, Pago } from '../../../core/models/cobro.model';
import { AuthService } from '../../../core/services/auth.service';
import { CobroService } from '../../../core/services/cobro.service';
import { NombresAlumnoService } from '../../../core/services/nombres-alumno.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const hoy = (): string => new Date().toISOString().slice(0, 10);
const inicioMes = (): string => hoy().slice(0, 8) + '01';

@Component({
  selector: 'app-cobranza',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, ModalComponent, PageHeaderComponent],
  templateUrl: './cobranza.component.html'
})
export class CobranzaComponent implements OnInit {
  private readonly api = inject(CobroService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);
  protected readonly nombres = inject(NombresAlumnoService);

  protected readonly esAdmin = inject(AuthService).isAdmin;
  protected readonly medios = MEDIOS_PAGO;

  protected readonly pagos = signal<Pago[]>([]);
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);

  protected readonly filtro = this.fb.nonNullable.group({ desde: inicioMes(), hasta: hoy(), medio: '' });

  protected readonly detalle = signal<Pago | null>(null);
  protected readonly anulando = signal<Pago | null>(null);
  protected readonly motivo = this.fb.nonNullable.control('');
  protected readonly errorForm = signal<string | null>(null);
  protected readonly guardando = signal(false);

  ngOnInit(): void {
    this.cargar(0);
  }

  protected irA(p: number): void {
    if (p >= 0 && p < this.paginas()) this.cargar(p);
  }

  protected cargar(pagina = 0): void {
    const f = this.filtro.getRawValue();
    this.cargando.set(true);
    this.api.reporteCobranza(f.desde || undefined, f.hasta || undefined, f.medio || undefined, pagina).subscribe({
      next: p => {
        this.pagos.set(p.contenido);
        this.total.set(p.totalElementos);
        this.paginas.set(Math.max(1, p.totalPaginas));
        this.pagina.set(p.pagina);
        this.cargando.set(false);
        this.nombres.cargar(p.contenido.map(x => x.alumnoId));
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar el reporte.'));
      }
    });
  }

  protected totalPagina(): number {
    return this.pagos().filter(p => p.estado === 'CONFIRMADO').reduce((s, p) => s + p.montoTotal, 0);
  }

  protected ver(p: Pago): void {
    this.detalle.set(p);
  }

  protected cerrar(): void {
    this.detalle.set(null);
    this.anulando.set(null);
  }

  protected pedirAnulacion(p: Pago): void {
    this.motivo.setValue('');
    this.errorForm.set(null);
    this.anulando.set(p);
  }

  protected anular(): void {
    const p = this.anulando();
    if (!p) return;
    if (!this.motivo.value.trim()) {
      this.errorForm.set('El motivo es obligatorio.');
      return;
    }
    this.guardando.set(true);
    this.api.anular(p.id, this.motivo.value.trim()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success('Pago anulado. Las cuotas vuelven a quedar pendientes y el comprobante se anula.');
        this.cerrar();
        this.cargar(this.pagina());
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }
}
