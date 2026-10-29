import { Component, computed, inject, input, output, signal } from '@angular/core';
import { AlumnoSeccion } from '../../core/models/matricula.model';
import { Seccion } from '../../core/models/planificacion.model';
import { MatriculaService } from '../../core/services/matricula.service';
import { NotificationService } from '../../core/services/notification.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';

/** Pasa a un alumno a otra sección del mismo grado (el servicio solo permite traslados dentro del grado y el año). */
@Component({
  selector: 'app-trasladar-alumno',
  imports: [ModalComponent],
  template: `
    <app-modal [titulo]="'Trasladar a ' + alumno().alumnoNombre" [ancho]="480" (cerrar)="cerrar.emit()">
      <div class="form-grid">
        <div class="field full">
          <label for="tr-sec">Nueva sección ({{ origen().gradoNombre }})</label>
          <select id="tr-sec" (change)="destino.set(+$any($event.target).value)">
            <option value="0" [selected]="destino() === 0" disabled>Elige…</option>
            @for (s of opciones(); track s.id) { <option [value]="s.id">{{ s.nombre }} · {{ s.aulaNombre ?? 'sin aula' }} · cap. {{ s.capacidad }}</option> }
          </select>
          @if (opciones().length === 0) { <span class="muted">Este grado no tiene otra sección activa a la que trasladar.</span> }
        </div>
        <div class="field full">
          <label for="tr-mot">Motivo</label>
          <input id="tr-mot" maxlength="200" placeholder="Ej. equilibrio de aulas" (input)="motivo.set($any($event.target).value)" />
        </div>
      </div>
      @if (error()) { <div class="aviso aviso-danger" style="margin-top: 14px" role="alert">{{ error() }}</div> }
      <ng-container footer>
        <button type="button" class="btn" (click)="cerrar.emit()">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="destino() === 0 || !motivo().trim() || guardando()" (click)="guardar()">Trasladar</button>
      </ng-container>
    </app-modal>
  `
})
export class TrasladarAlumnoComponent {
  private readonly matriculas = inject(MatriculaService);
  private readonly avisos = inject(NotificationService);

  readonly alumno = input.required<AlumnoSeccion>();
  readonly origen = input.required<Seccion>();
  readonly secciones = input.required<Seccion[]>();
  readonly guardado = output<void>();
  readonly cerrar = output<void>();

  protected readonly destino = signal(0);
  protected readonly motivo = signal('');
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly opciones = computed(() => this.secciones().filter(s => s.gradoId === this.origen().gradoId && s.id !== this.origen().id && s.activo));

  protected guardar(): void {
    this.guardando.set(true);
    this.error.set(null);
    this.matriculas.trasladar(this.alumno().matriculaId, this.destino(), this.motivo().trim()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success('Alumno trasladado de sección.');
        this.guardado.emit();
      },
      error: e => {
        this.guardando.set(false);
        this.error.set(mensajeError(e));
      }
    });
  }
}
