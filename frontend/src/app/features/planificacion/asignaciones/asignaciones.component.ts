import { Component, OnInit, inject, signal } from '@angular/core';
import { catchError, forkJoin, of } from 'rxjs';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Anio, Asignacion, Curso, Docente, Seccion } from '../../../core/models/planificacion.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError, nombreCompleto } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-asignaciones',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './asignaciones.component.html'
})
export class AsignacionesComponent implements OnInit {
  private readonly api = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly anios = signal<Anio[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly docentes = signal<Docente[]>([]);
  protected readonly cursosMalla = signal<Curso[]>([]);
  protected readonly asignaciones = signal<Asignacion[]>([]);
  protected readonly cargando = signal(false);

  protected readonly anioId = this.fb.nonNullable.control(0);
  protected readonly seccionId = this.fb.nonNullable.control(0);

  protected readonly abierto = signal(false);
  protected readonly editando = signal<Asignacion | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    cursoId: [0, [Validators.required, Validators.min(1)]],
    docenteId: [0, [Validators.required, Validators.min(1)]]
  });

  protected nombreDocente = nombreCompleto;

  ngOnInit(): void {
    this.api.docentes('', 0, 200).subscribe(p => this.docentes.set(p.contenido.filter(d => d.estado === 'ACTIVO')));
    this.api.anios().subscribe(a => {
      this.anios.set(a);
      this.anioId.setValue((a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0);
      this.cargarSecciones();
    });
  }

  protected cargarSecciones(): void {
    this.seccionId.setValue(0);
    this.asignaciones.set([]);
    if (!this.anioId.value) return;
    forkJoin({ secciones: this.api.secciones(this.anioId.value), todas: this.api.asignaciones(undefined, undefined, this.anioId.value).pipe(catchError(() => of([] as Asignacion[]))) }).subscribe(({ secciones, todas }) => {
      this.secciones.set(secciones);
      // Se abre la primera sección que ya tiene docentes asignados (si ninguna, la primera) para no mostrar una pantalla vacía.
      const con = new Set(todas.map(a => a.seccionId));
      this.seccionId.setValue((secciones.find(x => con.has(x.id)) ?? secciones[0])?.id ?? 0);
      this.cargar();
    });
  }

  protected cargar(): void {
    const sec = this.secciones().find(s => s.id === this.seccionId.value);
    if (!sec) return;
    this.cargando.set(true);
    this.api.asignaciones(sec.id).subscribe({
      next: a => {
        this.asignaciones.set(a);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las asignaciones.'));
      }
    });
    // Solo se asignan los cursos que están en la malla del grado de la sección.
    this.api.malla(sec.gradoId).subscribe(m => {
      this.cursosMalla.set(m.cursos.map(c => ({ id: c.cursoId, codigo: c.cursoCodigo, nombre: c.cursoNombre, activo: true })));
    });
  }

  protected sinAsignar(): Curso[] {
    const usados = new Set(this.asignaciones().map(a => a.cursoId));
    return this.cursosMalla().filter(c => !usados.has(c.id));
  }

  protected nueva(): void {
    this.form.reset({ cursoId: 0, docenteId: 0 });
    this.form.controls.cursoId.enable();
    this.editando.set(null);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected cambiarDocente(a: Asignacion): void {
    this.form.reset({ cursoId: a.cursoId, docenteId: a.docenteId });
    this.form.controls.cursoId.disable();
    this.editando.set(a);
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
    (e
      ? this.api.cambiarDocenteAsignacion(e.id, v.docenteId)
      : this.api.crearAsignacion({ seccionId: this.seccionId.value, cursoId: v.cursoId, docenteId: v.docenteId })
    ).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Docente cambiado.' : 'Asignación creada.');
        this.cerrar();
        this.cargar();
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async eliminar(a: Asignacion): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Quitar a ${a.docenteNombre} de ${a.cursoNombre}? También se quitan sus clases del horario.`, 'Quitar', true))) return;
    this.api.eliminarAsignacion(a.id).subscribe({
      next: () => {
        this.avisos.success('Asignación eliminada.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
