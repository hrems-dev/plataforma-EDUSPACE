import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ArchivoRef, TareaPendiente } from '../../../core/models/tarea.model';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { TareaService } from '../../../core/services/tarea.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { SelectorHijoComponent } from '../../../shared/components/selector-hijo/selector-hijo.component';

const MAX_BYTES = 10 * 1024 * 1024;

@Component({
  selector: 'app-familia-tareas',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent, SelectorHijoComponent],
  templateUrl: './familia-tareas.component.html'
})
export class FamiliaTareasComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  private readonly api = inject(TareaService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly tareas = signal<TareaPendiente[]>([]);
  protected readonly cargando = signal(true);
  protected readonly delHijo = computed(() =>
    this.tareas()
      .filter(t => t.alumnoId === this.ctx.seleccionado()?.alumnoId)
      .sort((a, b) => a.fechaEntrega.localeCompare(b.fechaEntrega))
  );

  protected readonly entregando = signal<TareaPendiente | null>(null);
  protected readonly archivos = signal<File[]>([]);
  protected readonly enviando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly comentario = this.fb.nonNullable.control('');

  ngOnInit(): void {
    this.ctx.cargar().subscribe({ error: e => this.avisos.error(mensajeError(e)) });
    this.recargar();
  }

  protected clase(e: string): string {
    return e === 'CALIFICADA' ? 'badge-ok' : e === 'ENTREGADA' ? 'badge-warn' : e === 'DEVUELTA' ? 'badge-violet' : 'badge-gray';
  }

  protected texto(e: string): string {
    return e === 'SIN_ENTREGAR' ? 'Sin entregar' : e.charAt(0) + e.slice(1).toLowerCase();
  }

  private recargar(): void {
    this.cargando.set(true);
    this.api.mias().subscribe({
      next: t => {
        this.tareas.set(t);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las tareas.'));
      }
    });
  }

  // --- entregar (solo el alumno) ---
  protected abrir(t: TareaPendiente): void {
    this.entregando.set(t);
    this.archivos.set([]);
    this.comentario.setValue('');
    this.errorForm.set(null);
  }

  protected cerrar(): void {
    this.entregando.set(null);
  }

  protected elegirArchivos(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const lista = Array.from(input.files ?? []);
    if (lista.some(f => f.size > MAX_BYTES)) {
      this.avisos.error('Cada archivo debe pesar menos de 10 MB.');
      input.value = '';
      this.archivos.set([]);
      return;
    }
    this.archivos.set(lista);
  }

  protected entregar(): void {
    const t = this.entregando();
    if (!t) return;
    if (this.archivos().length === 0 && !this.comentario.value.trim()) {
      this.errorForm.set('Adjunta un archivo o escribe un comentario.');
      return;
    }
    this.enviando.set(true);
    this.errorForm.set(null);
    this.subirTodos(t.tareaId, this.archivos(), []).then(
      refs =>
        this.api.entregar(t.tareaId, this.comentario.value.trim() || undefined, refs).subscribe({
          next: () => {
            this.enviando.set(false);
            this.avisos.success('Tarea entregada.');
            this.cerrar();
            this.recargar();
          },
          error: e => {
            this.enviando.set(false);
            this.errorForm.set(mensajeError(e, 'No se pudo entregar la tarea.'));
          }
        }),
      () => {
        this.enviando.set(false);
        this.errorForm.set('No se pudo subir uno de los archivos. Intenta de nuevo.');
      }
    );
  }

  /** Sube los archivos uno por uno (URL firmada de MinIO) y devuelve sus referencias para la entrega. */
  private async subirTodos(tareaId: number, pendientes: File[], listos: ArchivoRef[]): Promise<ArchivoRef[]> {
    for (const f of pendientes) {
      listos.push(await new Promise<ArchivoRef>((ok, error) => this.api.subirArchivoEntrega(tareaId, f).subscribe({ next: ok, error })));
    }
    return listos;
  }
}
