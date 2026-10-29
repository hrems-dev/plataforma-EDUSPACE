import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Anio, Aula, Docente, Grado, Seccion, Turno } from '../../core/models/planificacion.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';

/** Crea o edita una sección (grado, nombre, turno, capacidad, aula y tutor) sin salir del panel de aulas. */
@Component({
  selector: 'app-seccion-form',
  imports: [ReactiveFormsModule, ModalComponent],
  template: `
    <app-modal [titulo]="editar() ? 'Editar sección ' + editar()!.nombre : 'Nueva sección'" [ancho]="560" (cerrar)="cerrar.emit()">
      <form [formGroup]="form" class="form-grid" (ngSubmit)="guardar()" id="form-seccion">
        <div class="field">
          <label for="sf-grado">Grado</label>
          <select id="sf-grado" formControlName="gradoId" [attr.disabled]="editar() ? '' : null">
            <option [ngValue]="0" disabled>Elige…</option>
            @for (g of grados(); track g.id) { <option [ngValue]="g.id">{{ g.nombre }}</option> }
          </select>
        </div>
        <div class="field">
          <label for="sf-nom">Nombre (letra)</label>
          <input id="sf-nom" formControlName="nombre" maxlength="10" placeholder="A" />
        </div>
        <div class="field">
          <label for="sf-turno">Turno</label>
          <select id="sf-turno" formControlName="turno"><option value="MANANA">Mañana</option><option value="TARDE">Tarde</option></select>
        </div>
        <div class="field">
          <label for="sf-cap">Capacidad</label>
          <input id="sf-cap" type="number" min="1" formControlName="capacidad" />
        </div>
        <div class="field">
          <label for="sf-aula">Aula</label>
          <select id="sf-aula" formControlName="aulaId">
            <option [ngValue]="0">Sin aula</option>
            @for (a of aulas(); track a.id) { <option [ngValue]="a.id">{{ a.codigo }} · {{ a.nombre }} (cap. {{ a.capacidad }})</option> }
          </select>
        </div>
        <div class="field">
          <label for="sf-tutor">Tutor</label>
          <select id="sf-tutor" formControlName="tutorDocenteId">
            <option [ngValue]="0">Sin tutor</option>
            @for (d of docentes(); track d.id) { <option [ngValue]="d.id">{{ d.apellidos }}, {{ d.nombres }}</option> }
          </select>
        </div>
        <label class="field full" style="flex-direction: row; gap: 8px; align-items: center"><input type="checkbox" formControlName="activo" /> Sección activa</label>
      </form>
      @if (error()) { <div class="aviso aviso-danger" style="margin-top: 14px" role="alert">{{ error() }}</div> }
      <ng-container footer>
        @if (editar()) { <button type="button" class="btn btn-danger" style="margin-right: auto" (click)="eliminar()">Eliminar</button> }
        <button type="button" class="btn" (click)="cerrar.emit()">Cancelar</button>
        <button type="submit" form="form-seccion" class="btn btn-primary" [disabled]="guardando()">Guardar</button>
      </ng-container>
    </app-modal>
  `
})
export class SeccionFormComponent implements OnInit {
  private readonly api = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  readonly anio = input.required<Anio>();
  readonly editar = input<Seccion | null>(null);
  /** Grado con el que arranca una sección nueva. */
  readonly gradoInicial = input(0);
  readonly guardado = output<void>();
  readonly cerrar = output<void>();

  protected readonly grados = signal<Grado[]>([]);
  protected readonly aulas = signal<Aula[]>([]);
  protected readonly docentes = signal<Docente[]>([]);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    gradoId: [0, [Validators.required, Validators.min(1)]],
    nombre: ['', [Validators.required, Validators.maxLength(10)]],
    turno: ['MANANA' as Turno, Validators.required],
    capacidad: [30, [Validators.required, Validators.min(1)]],
    aulaId: [0],
    tutorDocenteId: [0],
    activo: [true]
  });

  ngOnInit(): void {
    this.api.grados().subscribe(g => this.grados.set(g));
    this.api.aulas().subscribe(a => this.aulas.set(a.filter(x => x.activo)));
    this.api.docentes('', 0, 200).subscribe(p => this.docentes.set(p.contenido.filter(d => d.estado === 'ACTIVO')));
    const s = this.editar();
    if (s) {
      this.form.reset({ gradoId: s.gradoId, nombre: s.nombre, turno: s.turno, capacidad: s.capacidad, aulaId: s.aulaId ?? 0, tutorDocenteId: s.tutorDocenteId ?? 0, activo: s.activo });
    } else {
      this.form.patchValue({ gradoId: this.gradoInicial() });
    }
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Revisa el grado, el nombre y la capacidad.');
      return;
    }
    const v = this.form.getRawValue();
    const body = { anioId: this.anio().id, ...v, nombre: v.nombre.trim(), aulaId: v.aulaId || null, tutorDocenteId: v.tutorDocenteId || null };
    const e = this.editar();
    this.guardando.set(true);
    this.error.set(null);
    (e ? this.api.actualizarSeccion(e.id, body) : this.api.crearSeccion(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Sección actualizada.' : 'Sección creada.');
        this.guardado.emit();
      },
      error: err => {
        this.guardando.set(false);
        this.error.set(mensajeError(err));
      }
    });
  }

  protected async eliminar(): Promise<void> {
    const s = this.editar();
    if (!s || !(await this.confirmacion.confirmar(`¿Eliminar la sección ${s.gradoNombre} "${s.nombre}"?`, 'Eliminar', true))) return;
    this.api.eliminarSeccion(s.id).subscribe({
      next: () => {
        this.avisos.success('Sección eliminada.');
        this.guardado.emit();
      },
      error: e => this.error.set(mensajeError(e))
    });
  }
}
