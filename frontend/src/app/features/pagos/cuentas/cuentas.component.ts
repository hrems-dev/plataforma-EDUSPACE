import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AlumnoResumen } from '../../../core/models/alumno.model';
import { EstadoCuenta, EstadoCuota } from '../../../core/models/cobro.model';
import { Descuento } from '../../../core/models/pago.model';
import { Anio } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { CobroService } from '../../../core/services/cobro.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PagoConfigService } from '../../../core/services/pago-config.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { BuscadorAlumnoComponent } from '../../../shared/components/buscador-alumno/buscador-alumno.component';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const CLASE: Record<EstadoCuota, string> = { PENDIENTE: 'badge-warn', PARCIAL: 'badge-violet', PAGADA: 'badge-ok', VENCIDA: 'badge-danger', ANULADA: 'badge-gray' };

@Component({
  selector: 'app-cuentas',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, BuscadorAlumnoComponent, ModalComponent, PageHeaderComponent],
  templateUrl: './cuentas.component.html'
})
export class CuentasComponent {
  private readonly api = inject(CobroService);
  private readonly config = inject(PagoConfigService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  /** Solo el ADMIN asigna descuentos; la secretaria consulta la cuenta. */
  protected readonly esAdmin = inject(AuthService).isAdmin;

  protected readonly alumno = signal<AlumnoResumen | null>(null);
  protected readonly cuenta = signal<EstadoCuenta | null>(null);
  protected readonly cargando = signal(false);

  protected readonly abierto = signal(false);
  protected readonly descuentos = signal<Descuento[]>([]);
  protected readonly anios = signal<Anio[]>([]);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    descuentoId: [0, [Validators.required, Validators.min(1)]],
    anioId: [0, [Validators.required, Validators.min(1)]],
    motivo: ['']
  });

  protected clase(e: EstadoCuota): string {
    return CLASE[e];
  }

  protected elegir(a: AlumnoResumen | null): void {
    this.alumno.set(a);
    this.cuenta.set(null);
    if (a) this.cargar();
  }

  private cargar(): void {
    const a = this.alumno();
    if (!a) return;
    this.cargando.set(true);
    this.api.estadoCuenta(a.id).subscribe({
      next: c => {
        this.cuenta.set(c);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar la cuenta del alumno.'));
      }
    });
  }

  protected abrirDescuento(): void {
    this.errorForm.set(null);
    this.config.descuentos().subscribe(d => this.descuentos.set(d.filter(x => x.activo)));
    this.plan.anios().subscribe(a => {
      this.anios.set(a);
      this.form.reset({ descuentoId: 0, anioId: (a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0, motivo: '' });
    });
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
  }

  protected asignar(): void {
    const a = this.alumno();
    if (!a || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.api.asignarDescuento(a.id, v.descuentoId, v.anioId, v.motivo.trim() || undefined).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success('Descuento asignado. Se aplica a las cuotas pendientes que correspondan.');
        this.cerrar();
        this.cargar();
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }
}
