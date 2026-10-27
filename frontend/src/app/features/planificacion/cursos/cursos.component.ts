import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Curso } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-cursos',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './cursos.component.html'
})
export class CursosComponent implements OnInit {
  private readonly api = inject(PlanificacionService);

  /** En planificación solo el ADMIN escribe (horarios, asignaciones y docentes también los edita SECRETARIA). */
  protected readonly puedeEditar = inject(AuthService).isAdmin;
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly cursos = signal<Curso[]>([]);
  protected readonly cargando = signal(false);
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Curso | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(20)]],
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    area: [''],
    activo: [true]
  });

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.api.cursos().subscribe({
      next: c => {
        this.cursos.set(c);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los cursos.'));
      }
    });
  }

  protected nuevo(): void {
    this.form.reset({ codigo: '', nombre: '', area: '', activo: true });
    this.abrir(null);
  }

  protected editar(c: Curso): void {
    this.form.reset({ codigo: c.codigo, nombre: c.nombre, area: c.area ?? '', activo: c.activo });
    this.abrir(c);
  }

  private abrir(c: Curso | null): void {
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
    const body = { codigo: v.codigo.trim(), nombre: v.nombre.trim(), area: v.area.trim() || undefined, activo: v.activo };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarCurso(e.id, body) : this.api.crearCurso(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Curso actualizado.' : 'Curso creado.');
        this.cerrar();
        this.cargar();
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async eliminar(c: Curso): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar el curso ${c.nombre}?`, 'Eliminar', true))) return;
    this.api.eliminarCurso(c.id).subscribe({
      next: () => {
        this.avisos.success('Curso eliminado.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
