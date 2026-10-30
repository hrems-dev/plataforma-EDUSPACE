import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EstadoTarea, Tarea } from '../../../core/models/tarea.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ContextoDocente, DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { TareaService } from '../../../core/services/tarea.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { TareaEntregasComponent } from './tarea-entregas.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const CLASE: Record<EstadoTarea, string> = { BORRADOR: 'badge-gray', PUBLICADA: 'badge-ok', CERRADA: 'badge-warn' };

/** `datetime-local` no lleva zona horaria: se envía como hora local sin segundos de más. */
const aLocal = (iso: string): string => iso.slice(0, 16);

@Component({
  selector: 'app-docente-tareas',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, ModalComponent, PageHeaderComponent, TareaEntregasComponent],
  templateUrl: './docente-tareas.component.html'
})
export class DocenteTareasComponent implements OnInit {
  private readonly api = inject(TareaService);
  private readonly contexto = inject(DocenteContextoService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  /** Dentro de la página de un curso: solo las tareas de esa sección y curso, sin cabecera, y las entregas se abren aquí mismo. */
  readonly seccionId = input<number | null>(null);
  readonly cursoId = input<number | null>(null);
  readonly embebido = input(false);
  protected readonly viendo = signal<Tarea | null>(null);

  protected readonly ctx = signal<ContextoDocente | null>(null);
  protected readonly tareas = signal<Tarea[]>([]);
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);
  protected readonly estado = this.fb.nonNullable.control<EstadoTarea | ''>('');

  protected readonly abierto = signal(false);
  protected readonly editando = signal<Tarea | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    asignacion: [0, [Validators.required, Validators.min(1)]],
    periodoId: [0, [Validators.required, Validators.min(1)]],
    titulo: ['', [Validators.required, Validators.maxLength(150)]],
    descripcion: [''],
    fechaEntrega: ['', Validators.required],
    permiteTardia: [false],
    puntajeMaximo: [20, [Validators.required, Validators.min(1)]]
  });

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c => {
        this.ctx.set(c);
        this.cargar(0);
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo cargar tu información de docente.'))
    });
  }

  protected clase(e: EstadoTarea): string {
    return CLASE[e];
  }

  protected etiqueta(t: Tarea): string {
    const a = this.ctx()?.asignaciones.find(x => x.seccionId === t.seccionId && x.cursoId === t.cursoId);
    return a ? `${a.cursoNombre} · ${a.seccionNombre}` : `Sección ${t.seccionId}`;
  }

  protected irA(p: number): void {
    if (p >= 0 && p < this.paginas()) this.cargar(p);
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.api.listar({ estado: this.estado.value, seccionId: this.seccionId() ?? undefined, cursoId: this.cursoId() ?? undefined }, pagina).subscribe({
      next: p => {
        this.tareas.set(p.contenido);
        this.total.set(p.totalElementos);
        this.paginas.set(Math.max(1, p.totalPaginas));
        this.pagina.set(p.pagina);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las tareas.'));
      }
    });
  }

  // --- crear / editar ---
  protected nueva(): void {
    const c = this.ctx();
    const manana = new Date(Date.now() + 3 * 86_400_000);
    manana.setHours(23, 59, 0, 0);
    this.form.reset({
      asignacion: c?.asignaciones.find(a => a.seccionId === this.seccionId() && a.cursoId === this.cursoId())?.id ?? c?.asignaciones[0]?.id ?? 0,
      periodoId: c?.periodoActivo?.id ?? 0,
      titulo: '',
      descripcion: '',
      fechaEntrega: new Date(manana.getTime() - manana.getTimezoneOffset() * 60_000).toISOString().slice(0, 16),
      permiteTardia: false,
      puntajeMaximo: 20
    });
    this.form.controls.asignacion.enable();
    this.form.controls.periodoId.enable();
    this.editando.set(null);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected editar(t: Tarea): void {
    const a = this.ctx()?.asignaciones.find(x => x.seccionId === t.seccionId && x.cursoId === t.cursoId);
    this.form.reset({
      asignacion: a?.id ?? 0,
      periodoId: t.periodoId,
      titulo: t.titulo,
      descripcion: t.descripcion ?? '',
      fechaEntrega: aLocal(t.fechaEntrega),
      permiteTardia: t.permiteTardia,
      puntajeMaximo: t.puntajeMaximo
    });
    this.form.controls.asignacion.disable();
    this.form.controls.periodoId.disable();
    this.form.controls.asignacion.setErrors(null);
    this.editando.set(t);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected cerrarEntregas(): void {
    this.viendo.set(null);
    this.cargar(this.pagina()); // las notas y entregas cambian los contadores
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
    const comun = { titulo: v.titulo.trim(), descripcion: v.descripcion.trim() || undefined, fechaEntrega: v.fechaEntrega + ':00', permiteTardia: v.permiteTardia, puntajeMaximo: v.puntajeMaximo };
    this.guardando.set(true);
    const asig = this.ctx()?.asignaciones.find(a => a.id === v.asignacion);
    const peticion = e
      ? this.api.actualizar(e.id, comun)
      : this.api.crear({ ...comun, periodoId: v.periodoId, seccionId: asig!.seccionId, cursoId: asig!.cursoId });
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Tarea actualizada.' : 'Tarea creada como borrador. Publícala para que los alumnos la vean.');
        this.cerrar();
        this.cargar(this.pagina());
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async publicar(t: Tarea): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Publicar "${t.titulo}"? Los alumnos y apoderados recibirán un aviso.`, 'Publicar'))) return;
    this.api.publicar(t.id).subscribe({
      next: () => {
        this.avisos.success('Tarea publicada.');
        this.cargar(this.pagina());
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected async cerrarTarea(t: Tarea): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Cerrar "${t.titulo}"? Ya no se recibirán más entregas.`, 'Cerrar tarea', true))) return;
    this.api.cerrar(t.id).subscribe({
      next: () => {
        this.avisos.success('Tarea cerrada.');
        this.cargar(this.pagina());
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
