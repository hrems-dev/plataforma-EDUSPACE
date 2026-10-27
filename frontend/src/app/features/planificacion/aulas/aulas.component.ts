import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Aula, TIPOS_AULA, TipoAula } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-aulas',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './aulas.component.html'
})
export class AulasComponent implements OnInit {
  private readonly api = inject(PlanificacionService);

  /** En planificación solo el ADMIN escribe (horarios, asignaciones y docentes también los edita SECRETARIA). */
  protected readonly puedeEditar = inject(AuthService).isAdmin;
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly tipos = TIPOS_AULA;
  protected readonly aulas = signal<Aula[]>([]);
  protected readonly cargando = signal(false);
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Aula | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly filtroTipo = this.fb.nonNullable.control<TipoAula | ''>('');

  protected readonly form = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(20)]],
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    tipo: ['AULA' as TipoAula, Validators.required],
    capacidad: [30, [Validators.required, Validators.min(1)]],
    activo: [true]
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.api.aulas(this.filtroTipo.value || undefined).subscribe({
      next: a => {
        this.aulas.set(a);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las aulas.'));
      }
    });
  }

  protected nueva(): void {
    this.form.reset({ codigo: '', nombre: '', tipo: 'AULA', capacidad: 30, activo: true });
    this.abrir(null);
  }

  protected editar(a: Aula): void {
    this.form.reset({ codigo: a.codigo, nombre: a.nombre, tipo: a.tipo, capacidad: a.capacidad, activo: a.activo });
    this.abrir(a);
  }

  private abrir(a: Aula | null): void {
    this.errorForm.set(null);
    this.editando.set(a);
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
    const body = { ...v, codigo: v.codigo.trim(), nombre: v.nombre.trim() };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarAula(e.id, body) : this.api.crearAula(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Aula actualizada.' : 'Aula creada.');
        this.cerrar();
        this.cargar();
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async eliminar(a: Aula): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar el aula ${a.nombre}?`, 'Eliminar', true))) return;
    this.api.eliminarAula(a.id).subscribe({
      next: () => {
        this.avisos.success('Aula eliminada.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
