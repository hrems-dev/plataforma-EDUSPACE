import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Concepto, TIPOS_CONCEPTO, TipoConcepto } from '../../../core/models/pago.model';
import { NotificationService } from '../../../core/services/notification.service';
import { PagoConfigService } from '../../../core/services/pago-config.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-conceptos',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './conceptos.component.html'
})
export class ConceptosComponent implements OnInit {
  private readonly api = inject(PagoConfigService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly tipos = TIPOS_CONCEPTO;
  protected readonly conceptos = signal<Concepto[]>([]);
  protected readonly cargando = signal(false);
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Concepto | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(20)]],
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    tipo: ['PENSION' as TipoConcepto, Validators.required],
    activo: [true]
  });

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.api.conceptos().subscribe({
      next: c => {
        this.conceptos.set(c);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los conceptos.'));
      }
    });
  }

  protected nuevo(): void {
    this.form.reset({ codigo: '', nombre: '', tipo: 'PENSION', activo: true });
    this.abrir(null);
  }

  protected editar(c: Concepto): void {
    this.form.reset({ codigo: c.codigo, nombre: c.nombre, tipo: c.tipo, activo: c.activo });
    this.abrir(c);
  }

  private abrir(c: Concepto | null): void {
    this.errorForm.set(null);
    this.editando.set(c);
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
    const body = { ...v, codigo: v.codigo.trim().toUpperCase(), nombre: v.nombre.trim() };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarConcepto(e.id, body) : this.api.crearConcepto(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Concepto actualizado.' : 'Concepto creado.');
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
