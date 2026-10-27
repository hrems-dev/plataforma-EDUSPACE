import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Curso, Grado, MallaGrado, MallaItem } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-malla',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './malla.component.html'
})
export class MallaComponent implements OnInit {
  private readonly api = inject(PlanificacionService);

  /** En planificación solo el ADMIN escribe (horarios, asignaciones y docentes también los edita SECRETARIA). */
  protected readonly puedeEditar = inject(AuthService).isAdmin;
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly grados = signal<Grado[]>([]);
  protected readonly cursos = signal<Curso[]>([]);
  protected readonly malla = signal<MallaGrado | null>(null);
  protected readonly cargando = signal(false);

  protected readonly gradoId = this.fb.nonNullable.control(0);
  protected readonly abierto = signal(false);
  protected readonly editando = signal<MallaItem | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    cursoId: [0, [Validators.required, Validators.min(1)]],
    horasSemanales: [2, [Validators.required, Validators.min(1), Validators.max(40)]]
  });

  ngOnInit(): void {
    this.api.cursos().subscribe(c => this.cursos.set(c.filter(x => x.activo)));
    this.api.grados().subscribe(g => {
      this.grados.set(g);
      this.gradoId.setValue(g[0]?.id ?? 0);
      this.cargar();
    });
  }

  protected cargar(): void {
    if (!this.gradoId.value) return;
    this.cargando.set(true);
    this.api.malla(this.gradoId.value).subscribe({
      next: m => {
        this.malla.set(m);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar la malla.'));
      }
    });
  }

  /** Cursos del catálogo que aún no están en la malla de este grado. */
  protected disponibles(): Curso[] {
    const usados = new Set((this.malla()?.cursos ?? []).map(c => c.cursoId));
    return this.cursos().filter(c => !usados.has(c.id));
  }

  protected agregar(): void {
    this.form.reset({ cursoId: 0, horasSemanales: 2 });
    this.form.controls.cursoId.enable();
    this.editando.set(null);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected editar(i: MallaItem): void {
    this.form.reset({ cursoId: i.cursoId, horasSemanales: i.horasSemanales });
    this.form.controls.cursoId.disable();
    this.editando.set(i);
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
    const v = this.form.getRawValue();
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.cambiarHorasMalla(e.id, v.horasSemanales) : this.api.agregarCursoMalla(this.gradoId.value, v.cursoId, v.horasSemanales)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Horas actualizadas.' : 'Curso agregado a la malla.');
        this.cerrar();
        this.cargar();
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async quitar(i: MallaItem): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Quitar ${i.cursoNombre} de la malla de este grado?`, 'Quitar', true))) return;
    this.api.quitarCursoMalla(i.id).subscribe({
      next: () => {
        this.avisos.success('Curso quitado de la malla.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
