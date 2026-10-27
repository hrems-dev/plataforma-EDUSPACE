import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Anio, Aula, Docente, Grado, Seccion, Turno } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError, nombreCompleto } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-secciones',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './secciones.component.html'
})
export class SeccionesComponent implements OnInit {
  private readonly api = inject(PlanificacionService);

  /** En planificación solo el ADMIN escribe (horarios, asignaciones y docentes también los edita SECRETARIA). */
  protected readonly puedeEditar = inject(AuthService).isAdmin;
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly anios = signal<Anio[]>([]);
  protected readonly grados = signal<Grado[]>([]);
  protected readonly aulas = signal<Aula[]>([]);
  protected readonly docentes = signal<Docente[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly cargando = signal(false);

  protected readonly abierto = signal(false);
  protected readonly editando = signal<Seccion | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly filtro = this.fb.nonNullable.group({ anioId: 0, gradoId: 0 });

  protected readonly form = this.fb.nonNullable.group({
    anioId: [0, [Validators.required, Validators.min(1)]],
    gradoId: [0, [Validators.required, Validators.min(1)]],
    nombre: ['', [Validators.required, Validators.maxLength(10)]],
    turno: ['MANANA' as Turno, Validators.required],
    capacidad: [30, [Validators.required, Validators.min(1)]],
    aulaId: [0],
    tutorDocenteId: [0],
    activo: [true]
  });

  protected nombreDocente = nombreCompleto;

  ngOnInit(): void {
    this.api.anios().subscribe(a => {
      this.anios.set(a);
      const activo = a.find(x => x.estado === 'ACTIVO') ?? a[0];
      this.filtro.patchValue({ anioId: activo?.id ?? 0 });
      this.cargar();
    });
    this.api.grados().subscribe(g => this.grados.set(g));
    this.api.aulas().subscribe(a => this.aulas.set(a.filter(x => x.activo)));
    this.api.docentes('', 0, 200).subscribe(p => this.docentes.set(p.contenido.filter(d => d.estado === 'ACTIVO')));
  }

  protected cargar(): void {
    const { anioId, gradoId } = this.filtro.getRawValue();
    this.cargando.set(true);
    this.api.secciones(anioId || undefined, gradoId || undefined).subscribe({
      next: s => {
        this.secciones.set(s);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las secciones.'));
      }
    });
  }

  protected nueva(): void {
    this.form.reset({ anioId: this.filtro.controls.anioId.value, gradoId: this.filtro.controls.gradoId.value, nombre: '', turno: 'MANANA', capacidad: 30, aulaId: 0, tutorDocenteId: 0, activo: true });
    this.abrir(null);
  }

  protected editar(s: Seccion): void {
    this.form.reset({
      anioId: s.anioId, gradoId: s.gradoId, nombre: s.nombre, turno: s.turno, capacidad: s.capacidad,
      aulaId: s.aulaId ?? 0, tutorDocenteId: s.tutorDocenteId ?? 0, activo: s.activo
    });
    this.abrir(s);
  }

  private abrir(s: Seccion | null): void {
    this.errorForm.set(null);
    this.editando.set(s);
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
    const body = { ...v, nombre: v.nombre.trim(), aulaId: v.aulaId || null, tutorDocenteId: v.tutorDocenteId || null };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarSeccion(e.id, body) : this.api.crearSeccion(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Sección actualizada.' : 'Sección creada.');
        this.cerrar();
        this.cargar();
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async eliminar(s: Seccion): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar la sección ${s.gradoNombre} "${s.nombre}"?`, 'Eliminar', true))) return;
    this.api.eliminarSeccion(s.id).subscribe({
      next: () => {
        this.avisos.success('Sección eliminada.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
