import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { APLICA_A, AplicaA, Descuento, TIPOS_DESCUENTO, TipoDescuento } from '../../../core/models/pago.model';
import { NotificationService } from '../../../core/services/notification.service';
import { PagoConfigService } from '../../../core/services/pago-config.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-descuentos',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './descuentos.component.html'
})
export class DescuentosComponent implements OnInit {
  private readonly api = inject(PagoConfigService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly tipos = TIPOS_DESCUENTO;
  protected readonly aplicaA = APLICA_A;
  protected readonly descuentos = signal<Descuento[]>([]);
  protected readonly cargando = signal(false);
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Descuento | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    tipo: ['PORCENTAJE' as TipoDescuento, Validators.required],
    valor: [0, [Validators.required, Validators.min(0.01)]],
    aplicaA: ['PENSION' as AplicaA, Validators.required],
    activo: [true]
  });

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.api.descuentos().subscribe({
      next: d => {
        this.descuentos.set(d);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los descuentos.'));
      }
    });
  }

  protected valor(d: Descuento): string {
    return d.tipo === 'PORCENTAJE' ? `${d.valor} %` : `S/ ${d.valor.toFixed(2)}`;
  }

  protected nuevo(): void {
    this.form.reset({ nombre: '', tipo: 'PORCENTAJE', valor: 10, aplicaA: 'PENSION', activo: true });
    this.abrir(null);
  }

  protected editar(d: Descuento): void {
    this.form.reset({ nombre: d.nombre, tipo: d.tipo, valor: d.valor, aplicaA: d.aplicaA, activo: d.activo });
    this.abrir(d);
  }

  private abrir(d: Descuento | null): void {
    this.errorForm.set(null);
    this.editando.set(d);
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
    this.editando.set(null);
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (v.tipo === 'PORCENTAJE' && v.valor > 100) {
      this.errorForm.set('Un porcentaje no puede superar 100.');
      return;
    }
    const body = { ...v, nombre: v.nombre.trim() };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarDescuento(e.id, body) : this.api.crearDescuento(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Descuento actualizado. No cambia las cuotas ya generadas.' : 'Descuento creado.');
        this.cerrar();
        this.cargar();
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }
}
