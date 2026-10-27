import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Anio, Grado, Nivel } from '../../../core/models/planificacion.model';
import { Concepto, Tarifa } from '../../../core/models/pago.model';
import { NotificationService } from '../../../core/services/notification.service';
import { PagoConfigService } from '../../../core/services/pago-config.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

@Component({
  selector: 'app-tarifas',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './tarifas.component.html'
})
export class TarifasComponent implements OnInit {
  private readonly api = inject(PagoConfigService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly meses = MESES;
  protected readonly anios = signal<Anio[]>([]);
  protected readonly niveles = signal<Nivel[]>([]);
  protected readonly grados = signal<Grado[]>([]);
  protected readonly conceptos = signal<Concepto[]>([]);
  protected readonly tarifas = signal<Tarifa[]>([]);
  protected readonly cargando = signal(false);

  protected readonly abierto = signal(false);
  protected readonly editando = signal<Tarifa | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly anioId = this.fb.nonNullable.control(0);

  protected readonly form = this.fb.nonNullable.group({
    nivelId: [0, [Validators.required, Validators.min(1)]],
    gradoId: [0],
    conceptoId: [0, [Validators.required, Validators.min(1)]],
    monto: [0, [Validators.required, Validators.min(0.01)]],
    numCuotas: [10, [Validators.required, Validators.min(1), Validators.max(12)]],
    mesInicio: [3, [Validators.required, Validators.min(1), Validators.max(12)]],
    diaVencimiento: [5, [Validators.required, Validators.min(1), Validators.max(28)]],
    activo: [true]
  });

  ngOnInit(): void {
    this.plan.anios().subscribe(a => {
      this.anios.set(a);
      this.anioId.setValue((a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0);
      this.cargar();
    });
    this.plan.niveles().subscribe(n => this.niveles.set(n));
    this.plan.grados().subscribe(g => this.grados.set(g));
    this.api.conceptos().subscribe(c => this.conceptos.set(c.filter(x => x.activo)));
  }

  protected gradosDelNivel(): Grado[] {
    const nivel = this.form.controls.nivelId.value;
    return this.grados().filter(g => g.nivelId === nivel);
  }

  protected cargar(): void {
    if (!this.anioId.value) {
      this.tarifas.set([]);
      return;
    }
    this.cargando.set(true);
    this.api.tarifas(this.anioId.value).subscribe({
      next: t => {
        this.tarifas.set(t);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las tarifas.'));
      }
    });
  }

  protected nombreNivel(id: number): string {
    return this.niveles().find(n => n.id === id)?.nombre ?? String(id);
  }

  protected nombreGrado(id?: number): string {
    return id ? (this.grados().find(g => g.id === id)?.nombre ?? String(id)) : 'Todos los grados';
  }

  protected soles(n: number): string {
    return 'S/ ' + n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  protected nueva(): void {
    this.form.enable();
    this.form.reset({ nivelId: 0, gradoId: 0, conceptoId: 0, monto: 0, numCuotas: 10, mesInicio: 3, diaVencimiento: 5, activo: true });
    this.abrir(null);
  }

  protected editar(t: Tarifa): void {
    this.form.reset({
      nivelId: t.nivelId, gradoId: t.gradoId ?? 0, conceptoId: t.conceptoId, monto: t.monto,
      numCuotas: t.numCuotas, mesInicio: t.mesInicio, diaVencimiento: t.diaVencimiento, activo: t.activo
    });
    // Una tarifa existente solo se activa o desactiva: para otro monto o calendario se crea una nueva.
    this.form.disable();
    this.form.controls.activo.enable();
    this.abrir(t);
  }

  private abrir(t: Tarifa | null): void {
    this.errorForm.set(null);
    this.editando.set(t);
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
    this.editando.set(null);
  }

  protected cambioNivel(): void {
    this.form.controls.gradoId.setValue(0);
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = { ...v, anioId: this.anioId.value, gradoId: v.gradoId || null };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarTarifa(e.id, body) : this.api.crearTarifa(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Tarifa actualizada.' : 'Tarifa creada.');
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
