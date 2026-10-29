import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Asignacion, Curso, Docente, Seccion } from '../../core/models/planificacion.model';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { mensajeError, nombreCompleto } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';

const valor = (e: Event): number => Number((e.target as HTMLSelectElement).value);

/**
 * Asigna un docente a un curso de una sección (o cambia el docente de una asignación existente).
 * Solo se ofrecen los cursos de la malla del grado que aún no tienen docente en esa sección.
 */
@Component({
  selector: 'app-asignar-docente',
  imports: [ModalComponent],
  template: `
    <app-modal [titulo]="editar() ? 'Cambiar docente de ' + editar()!.cursoNombre : 'Asignar docente a un curso'" [ancho]="500" (cerrar)="cerrar.emit()">
      <div class="form-grid">
        <div class="field full">
          <label for="ad-sec">Sección</label>
          <select id="ad-sec" [disabled]="!!editar() || seccionId() > 0" (change)="elegirSeccion(valor($event), cursoId())">
            <option value="0" [selected]="seccion() === 0" disabled>Elige…</option>
            @for (s of secciones(); track s.id) { <option [value]="s.id" [selected]="s.id === seccion()">{{ s.gradoNombre }} · {{ s.nombre }}</option> }
          </select>
        </div>
        @if (!editar()) {
          <div class="field full">
            <label for="ad-cur">Curso (de la malla del grado)</label>
            <select id="ad-cur" [disabled]="seccion() === 0 || cargando()" (change)="curso.set(valor($event))">
              <option value="0" [selected]="curso() === 0" disabled>{{ seccion() === 0 ? 'Primero elige la sección' : 'Elige…' }}</option>
              @for (c of disponibles(); track c.id) { <option [value]="c.id" [selected]="c.id === curso()">{{ c.nombre }}</option> }
            </select>
            @if (seccion() > 0 && !cargando() && disponibles().length === 0) {
              <span class="muted">Todos los cursos de la malla ya tienen docente (o el grado no tiene malla). Agrega cursos en «Malla curricular».</span>
            }
          </div>
        }
        <div class="field full">
          <label for="ad-doc">Docente</label>
          <select id="ad-doc" (change)="docente.set(valor($event))">
            <option value="0" [selected]="docente() === 0" disabled>Elige…</option>
            @for (d of docentes(); track d.id) {
              <option [value]="d.id" [selected]="d.id === docente()">{{ nombre(d) }}{{ d.especialidad ? ' · ' + d.especialidad : '' }}</option>
            }
          </select>
        </div>
      </div>
      @if (error()) { <div class="aviso aviso-danger" style="margin-top: 14px" role="alert">{{ error() }}</div> }
      <ng-container footer>
        <button type="button" class="btn" (click)="cerrar.emit()">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="!valido() || guardando()" (click)="guardar()">Guardar</button>
      </ng-container>
    </app-modal>
  `
})
export class AsignarDocenteComponent implements OnInit {
  private readonly api = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);

  readonly secciones = input.required<Seccion[]>();
  readonly docentes = input.required<Docente[]>();
  /** Valores con los que abre el formulario (0 = sin elegir). */
  readonly seccionId = input(0);
  readonly cursoId = input(0);
  readonly docenteId = input(0);
  /** Si viene, solo se cambia el docente de esa asignación. */
  readonly editar = input<Asignacion | null>(null);
  readonly guardado = output<void>();
  readonly cerrar = output<void>();

  protected readonly seccion = signal(0);
  protected readonly curso = signal(0);
  protected readonly docente = signal(0);
  protected readonly cargando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly malla = signal<Curso[]>([]);
  private readonly usados = signal<Set<number>>(new Set());

  protected readonly disponibles = computed(() => this.malla().filter(c => !this.usados().has(c.id)));
  protected readonly valido = computed(() => this.docente() > 0 && (this.editar() ? true : this.seccion() > 0 && this.curso() > 0));

  protected readonly valor = valor;
  protected readonly nombre = nombreCompleto;

  ngOnInit(): void {
    const e = this.editar();
    this.docente.set(e?.docenteId ?? this.docenteId());
    this.seccion.set(e?.seccionId ?? this.seccionId());
    this.curso.set(this.cursoId());
    if (this.seccion() > 0) this.elegirSeccion(this.seccion(), this.cursoId());
  }

  protected elegirSeccion(id: number, cursoPrevio = 0): void {
    this.seccion.set(id);
    this.curso.set(0);
    this.malla.set([]);
    const sec = this.secciones().find(s => s.id === id);
    if (!sec || this.editar()) return;
    this.cargando.set(true);
    forkJoin({ malla: this.api.malla(sec.gradoId), asignadas: this.api.asignaciones(sec.id) }).subscribe({
      next: ({ malla, asignadas }) => {
        this.malla.set(malla.cursos.map(c => ({ id: c.cursoId, codigo: c.cursoCodigo, nombre: c.cursoNombre, activo: true })));
        this.usados.set(new Set(asignadas.map(a => a.cursoId)));
        if (cursoPrevio && this.disponibles().some(c => c.id === cursoPrevio)) this.curso.set(cursoPrevio);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No se pudo leer la malla de esa sección.'));
      }
    });
  }

  protected guardar(): void {
    if (!this.valido()) return;
    const e = this.editar();
    this.guardando.set(true);
    this.error.set(null);
    (e
      ? this.api.cambiarDocenteAsignacion(e.id, this.docente())
      : this.api.crearAsignacion({ seccionId: this.seccion(), cursoId: this.curso(), docenteId: this.docente() })
    ).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Docente cambiado.' : 'Docente asignado al curso.');
        this.guardado.emit();
      },
      error: err => {
        this.guardando.set(false);
        this.error.set(mensajeError(err));
      }
    });
  }
}
