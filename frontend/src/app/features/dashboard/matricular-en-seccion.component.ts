import { Component, inject, input, output, signal } from '@angular/core';
import { AlumnoResumen } from '../../core/models/alumno.model';
import { TIPOS_MATRICULA, TipoMatricula } from '../../core/models/matricula.model';
import { Seccion } from '../../core/models/planificacion.model';
import { AlumnoService } from '../../core/services/alumno.service';
import { MatriculaService } from '../../core/services/matricula.service';
import { NotificationService } from '../../core/services/notification.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';

const ETIQUETA: Record<TipoMatricula, string> = { NUEVA: 'Nueva', RATIFICACION: 'Ratificación', REINGRESO: 'Reingreso' };

/** Matricula a un alumno directamente en la sección que se está viendo (sin ir a la pantalla de matrículas). */
@Component({
  selector: 'app-matricular-en-seccion',
  imports: [ModalComponent],
  template: `
    <app-modal [titulo]="'Matricular en ' + seccion().gradoNombre + ' · ' + seccion().nombre" [ancho]="520" (cerrar)="cerrar.emit()">
      <div class="form-grid">
        <div class="field full">
          <label for="ms-q">Alumno (nombre o documento)</label>
          @if (elegido(); as a) {
            <div class="elegido">
              <span><strong>{{ a.nombreCompleto }}</strong> <span class="muted">· {{ a.numeroDocumento }}</span></span>
              <button type="button" class="enlace" (click)="elegido.set(null)">Cambiar</button>
            </div>
          } @else {
            <input id="ms-q" type="search" autocomplete="off" placeholder="Escribe al menos 2 letras…" (input)="buscar($any($event.target).value)" />
            @if (resultados().length > 0) {
              <ul class="resultados" role="listbox">
                @for (a of resultados(); track a.id) {
                  <li><button type="button" role="option" (click)="elegir(a)">{{ a.nombreCompleto }} <span class="muted">· {{ a.numeroDocumento }}</span></button></li>
                }
              </ul>
            } @else if (buscado()) {
              <span class="muted">No se encontró ningún alumno activo con ese dato. Regístralo en «Matrícula → Alumnos».</span>
            }
          }
        </div>
        <div class="field full">
          <label for="ms-tipo">Tipo de matrícula</label>
          <select id="ms-tipo" (change)="tipo.set($any($event.target).value)">
            @for (t of tipos; track t) { <option [value]="t" [selected]="t === tipo()">{{ etiqueta(t) }}</option> }
          </select>
        </div>
      </div>
      @if (error()) { <div class="aviso aviso-danger" style="margin-top: 14px" role="alert">{{ error() }}</div> }
      <ng-container footer>
        <button type="button" class="btn" (click)="cerrar.emit()">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="!elegido() || guardando()" (click)="guardar()">Matricular</button>
      </ng-container>
    </app-modal>
  `,
  styles: `
    .elegido { display: flex; justify-content: space-between; gap: 12px; padding: 8px 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--primary-soft); }
    .resultados { list-style: none; margin: 6px 0 0; padding: 0; border: 1px solid var(--line); border-radius: 8px; max-height: 220px; overflow: auto; }
    .resultados button { display: block; width: 100%; text-align: left; background: none; border: 0; padding: 9px 12px; cursor: pointer; font: inherit; color: inherit; }
    .resultados button:hover, .resultados button:focus-visible { background: var(--primary-soft); }
  `
})
export class MatricularEnSeccionComponent {
  private readonly alumnos = inject(AlumnoService);
  private readonly matriculas = inject(MatriculaService);
  private readonly avisos = inject(NotificationService);

  readonly seccion = input.required<Seccion>();
  readonly guardado = output<void>();
  readonly cerrar = output<void>();

  protected readonly tipos = TIPOS_MATRICULA;
  protected readonly etiqueta = (t: TipoMatricula) => ETIQUETA[t];
  protected readonly resultados = signal<AlumnoResumen[]>([]);
  protected readonly buscado = signal(false);
  protected readonly elegido = signal<AlumnoResumen | null>(null);
  protected readonly tipo = signal<TipoMatricula>('NUEVA');
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  private espera?: ReturnType<typeof setTimeout>;

  protected buscar(valor: string): void {
    clearTimeout(this.espera);
    const q = valor.trim();
    this.buscado.set(false);
    if (q.length < 2) {
      this.resultados.set([]);
      return;
    }
    this.espera = setTimeout(() => {
      this.alumnos.listar(q, 'ACTIVO', 0, 8).subscribe({
        next: p => {
          this.resultados.set(p.contenido);
          this.buscado.set(true);
        },
        error: e => this.error.set(mensajeError(e))
      });
    }, 250);
  }

  protected elegir(a: AlumnoResumen): void {
    this.elegido.set(a);
    this.resultados.set([]);
    this.error.set(null);
  }

  protected guardar(): void {
    const a = this.elegido();
    if (!a) return;
    this.guardando.set(true);
    this.error.set(null);
    this.matriculas.crear({ alumnoId: a.id, seccionId: this.seccion().id, tipo: this.tipo() }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(`${a.nombreCompleto} quedó en pre-matrícula en esta sección.`);
        this.guardado.emit();
      },
      error: e => {
        this.guardando.set(false);
        this.error.set(mensajeError(e));
      }
    });
  }
}
