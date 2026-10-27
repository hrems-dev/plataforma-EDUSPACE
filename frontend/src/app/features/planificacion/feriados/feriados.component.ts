import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Anio, Feriado } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-feriados',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './feriados.component.html'
})
export class FeriadosComponent implements OnInit {
  private readonly api = inject(PlanificacionService);

  /** En planificación solo el ADMIN escribe (horarios, asignaciones y docentes también los edita SECRETARIA). */
  protected readonly puedeEditar = inject(AuthService).isAdmin;
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly anios = signal<Anio[]>([]);
  protected readonly feriados = signal<Feriado[]>([]);
  protected readonly cargando = signal(false);
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Feriado | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly anioId = this.fb.nonNullable.control(0);

  protected readonly form = this.fb.nonNullable.group({
    fecha: ['', Validators.required],
    descripcion: ['', [Validators.required, Validators.maxLength(150)]]
  });

  ngOnInit(): void {
    this.api.anios().subscribe(a => {
      this.anios.set(a);
      this.anioId.setValue((a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0);
      this.cargar();
    });
  }

  protected cargar(): void {
    if (!this.anioId.value) return;
    this.cargando.set(true);
    this.api.feriados(this.anioId.value).subscribe({
      next: f => {
        this.feriados.set([...f].sort((a, b) => a.fecha.localeCompare(b.fecha)));
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los feriados.'));
      }
    });
  }

  protected nuevo(): void {
    this.form.reset({ fecha: '', descripcion: '' });
    this.editando.set(null);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected editar(f: Feriado): void {
    this.form.reset({ fecha: f.fecha, descripcion: f.descripcion });
    this.editando.set(f);
    this.errorForm.set(null);
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
    const v = { anioId: this.anioId.value, ...this.form.getRawValue(), descripcion: this.form.controls.descripcion.value.trim() };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarFeriado(e.id, v) : this.api.crearFeriado(v)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Feriado actualizado.' : 'Feriado registrado.');
        this.cerrar();
        this.cargar();
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async eliminar(f: Feriado): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar el feriado "${f.descripcion}"?`, 'Eliminar', true))) return;
    this.api.eliminarFeriado(f.id).subscribe({
      next: () => {
        this.avisos.success('Feriado eliminado.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
