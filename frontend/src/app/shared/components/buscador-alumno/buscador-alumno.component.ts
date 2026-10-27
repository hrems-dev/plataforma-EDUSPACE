import { Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AlumnoResumen } from '../../../core/models/alumno.model';
import { AlumnoService } from '../../../core/services/alumno.service';
import { mensajeError } from '../../../core/utils/errores';
import { NotificationService } from '../../../core/services/notification.service';

/** Caja de búsqueda de alumnos (nombre, código o documento) con resultados para elegir uno. */
@Component({
  selector: 'app-buscador-alumno',
  imports: [ReactiveFormsModule],
  template: `
    <div class="field">
      @if (etiqueta()) {
        <label [for]="idCampo">{{ etiqueta() }}</label>
      }
      <div class="toolbar">
        <input
          [id]="idCampo"
          class="input grow"
          type="search"
          [formControl]="q"
          [placeholder]="placeholder()"
          (keydown.enter)="$event.preventDefault(); buscar()"
        />
        <button type="button" class="btn" (click)="buscar()">Buscar</button>
      </div>
    </div>
    @if (elegido(); as a) {
      <div class="aviso aviso-ok" style="margin-top: 10px">
        <strong>{{ a.nombreCompleto }}</strong> · {{ a.codigo }}
        <button type="button" class="btn btn-sm" style="margin-left: 10px" (click)="limpiar()">Cambiar</button>
      </div>
    }
    @for (a of resultados(); track a.id) {
      <button type="button" class="btn btn-sm" style="margin: 8px 8px 0 0" (click)="elegir(a)">{{ a.nombreCompleto }} · {{ a.numeroDocumento }}</button>
    }
    @if (buscado() && resultados().length === 0 && !elegido()) {
      <div class="muted" style="margin-top: 8px">No se encontraron alumnos.</div>
    }
  `
})
export class BuscadorAlumnoComponent {
  private readonly api = inject(AlumnoService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  readonly etiqueta = input('Alumno');
  readonly placeholder = input('Nombre, código o documento (mín. 2 letras)');
  readonly soloActivos = input(true);
  readonly elegidoAlumno = output<AlumnoResumen | null>();

  protected readonly idCampo = `buscar-alumno-${Math.random().toString(36).slice(2, 8)}`;
  protected readonly q = this.fb.nonNullable.control('');
  protected readonly resultados = signal<AlumnoResumen[]>([]);
  protected readonly elegido = signal<AlumnoResumen | null>(null);
  protected readonly buscado = signal(false);

  protected buscar(): void {
    const texto = this.q.value.trim();
    if (texto.length < 2) {
      this.resultados.set([]);
      return;
    }
    this.api.listar(texto, this.soloActivos() ? 'ACTIVO' : '', 0, 8).subscribe({
      next: p => {
        this.resultados.set(p.contenido);
        this.buscado.set(true);
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected elegir(a: AlumnoResumen): void {
    this.elegido.set(a);
    this.resultados.set([]);
    this.elegidoAlumno.emit(a);
  }

  protected limpiar(): void {
    this.elegido.set(null);
    this.elegidoAlumno.emit(null);
  }
}
