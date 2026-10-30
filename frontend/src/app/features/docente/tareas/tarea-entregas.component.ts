import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EntregaAlumno, Tarea } from '../../../core/models/tarea.model';
import { NotificationService } from '../../../core/services/notification.service';
import { TareaService } from '../../../core/services/tarea.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-tarea-entregas',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './tarea-entregas.component.html'
})
export class TareaEntregasComponent {
  private readonly api = inject(TareaService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  readonly id = input.required<string>();
  /** Dentro de una ventana de la página del curso: sin cabecera ni «Volver». */
  readonly embebido = input(false);
  protected readonly tareaId = computed(() => Number(this.id()));

  protected readonly tarea = signal<Tarea | null>(null);
  protected readonly entregas = signal<EntregaAlumno[]>([]);
  protected readonly cargando = signal(true);

  protected readonly calificando = signal<{ fila: EntregaAlumno; devolver: boolean } | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({ nota: [0], retroalimentacion: [''] });

  constructor() {
    toObservable(this.tareaId).subscribe(id => this.cargar(id));
  }

  private cargar(id = this.tareaId()): void {
    this.cargando.set(true);
    this.api.detalle(id).subscribe({ next: t => this.tarea.set(t), error: e => this.avisos.error(mensajeError(e, 'No se pudo cargar la tarea.')) });
    this.api.entregas(id).subscribe({
      next: e => {
        this.entregas.set(e);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las entregas.'));
      }
    });
  }

  protected clase(e: string): string {
    return e === 'CALIFICADA' ? 'badge-ok' : e === 'ENTREGADA' ? 'badge-warn' : e === 'DEVUELTA' ? 'badge-violet' : 'badge-gray';
  }

  protected texto(e: string): string {
    return e === 'SIN_ENTREGAR' ? 'Sin entregar' : e.charAt(0) + e.slice(1).toLowerCase();
  }

  protected descargar(f: EntregaAlumno, archivoId: number): void {
    if (!f.entrega) return;
    this.api.urlArchivoEntrega(f.entrega.id, archivoId).subscribe({
      next: r => window.open(r.urlDescarga, '_blank', 'noopener,noreferrer'),
      error: e => this.avisos.error(mensajeError(e, 'No se pudo abrir el archivo.'))
    });
  }

  protected abrir(fila: EntregaAlumno, devolver: boolean): void {
    this.form.reset({ nota: fila.entrega?.nota ?? 0, retroalimentacion: fila.entrega?.retroalimentacion ?? '' });
    this.errorForm.set(null);
    this.calificando.set({ fila, devolver });
  }

  protected cerrar(): void {
    this.calificando.set(null);
  }

  protected guardar(): void {
    const c = this.calificando();
    const max = this.tarea()?.puntajeMaximo ?? 20;
    if (!c?.fila.entrega) return;
    const { nota, retroalimentacion } = this.form.getRawValue();
    if (c.devolver && !retroalimentacion.trim()) {
      this.errorForm.set('Para devolver la tarea explica qué debe corregir el alumno.');
      return;
    }
    if (!c.devolver && (nota < 0 || nota > max)) {
      this.errorForm.set(`La nota debe estar entre 0 y ${max}.`);
      return;
    }
    this.guardando.set(true);
    const id = c.fila.entrega.id;
    (c.devolver ? this.api.devolver(id, retroalimentacion.trim()) : this.api.calificar(id, nota, retroalimentacion.trim() || undefined)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(c.devolver ? 'Tarea devuelta al alumno.' : 'Calificación guardada. El alumno y su apoderado reciben un aviso.');
        this.cerrar();
        this.cargar();
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }
}
