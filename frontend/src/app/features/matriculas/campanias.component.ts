import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Campania, EstadoCampania } from '../../core/models/matricula.model';
import { Anio } from '../../core/models/planificacion.model';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { MatriculaService } from '../../core/services/matricula.service';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

const CLASE: Record<EstadoCampania, string> = { PLANIFICADA: 'badge-warn', ABIERTA: 'badge-ok', CERRADA: 'badge-gray' };

@Component({
  selector: 'app-campanias',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './campanias.component.html'
})
export class CampaniasComponent implements OnInit {
  private readonly api = inject(MatriculaService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  /** Crear, abrir y cerrar campañas es solo del ADMIN; la secretaria las consulta. */
  protected readonly esAdmin = inject(AuthService).isAdmin;

  protected readonly anios = signal<Anio[]>([]);
  protected readonly campanias = signal<Campania[]>([]);
  protected readonly cargando = signal(false);
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Campania | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly anioId = this.fb.nonNullable.control(0);
  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required]
  });

  ngOnInit(): void {
    this.plan.anios().subscribe(a => {
      this.anios.set(a);
      this.anioId.setValue((a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0);
      this.cargar();
    });
  }

  protected clase(e: EstadoCampania): string {
    return CLASE[e];
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.api.campanias(this.anioId.value || undefined).subscribe({
      next: c => {
        this.campanias.set(c);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las campañas.'));
      }
    });
  }

  protected nueva(): void {
    const anio = this.anios().find(a => a.id === this.anioId.value);
    this.form.reset({ nombre: anio ? `Matrícula ${anio.nombre}` : '', fechaInicio: '', fechaFin: '' });
    this.errorForm.set(null);
    this.editando.set(null);
    this.abierto.set(true);
  }

  protected editar(c: Campania): void {
    this.form.reset({ nombre: c.nombre, fechaInicio: c.fechaInicio, fechaFin: c.fechaFin });
    this.errorForm.set(null);
    this.editando.set(c);
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
    this.editando.set(null);
  }

  protected guardar(): void {
    if (this.form.invalid || !this.anioId.value) {
      this.form.markAllAsTouched();
      return;
    }
    const body = { anioId: this.anioId.value, ...this.form.getRawValue(), nombre: this.form.controls.nombre.value.trim() };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarCampania(e.id, body) : this.api.crearCampania(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Campaña actualizada.' : 'Campaña creada. Ábrela cuando empiece la matrícula.');
        this.cerrar();
        this.cargar();
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async abrirCampania(c: Campania): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Abrir la campaña "${c.nombre}"? Solo puede haber una abierta por año.`, 'Abrir'))) return;
    this.api.abrirCampania(c.id).subscribe({
      next: () => {
        this.avisos.success('Campaña abierta: ya se puede matricular.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected async cerrarCampania(c: Campania): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Cerrar la campaña "${c.nombre}"? Ya no se podrán crear matrículas nuevas.`, 'Cerrar campaña', true))) return;
    this.api.cerrarCampania(c.id).subscribe({
      next: () => {
        this.avisos.success('Campaña cerrada.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
