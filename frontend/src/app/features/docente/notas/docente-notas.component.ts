import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Criterio, NotaPendiente, OrigenCriterio, RegistroNotas, ValidarPesos } from '../../../core/models/calificacion.model';
import { Asignacion } from '../../../core/models/planificacion.model';
import { CalificacionService } from '../../../core/services/calificacion.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ContextoDocente, DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-docente-notas',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, ModalComponent, PageHeaderComponent],
  templateUrl: './docente-notas.component.html'
})
export class DocenteNotasComponent implements OnInit {
  private readonly api = inject(CalificacionService);
  private readonly contexto = inject(DocenteContextoService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  /** Dentro de la página de un curso: ya viene elegido el curso y se ocultan la cabecera y el selector de curso. */
  readonly seccionId = input<number | null>(null);
  readonly cursoId = input<number | null>(null);
  readonly embebido = input(false);

  protected readonly ctx = signal<ContextoDocente | null>(null);
  protected readonly registro = signal<RegistroNotas | null>(null);
  protected readonly pesos = signal<ValidarPesos | null>(null);
  protected readonly pendientes = signal<NotaPendiente[]>([]);
  protected readonly cargando = signal(false);
  protected readonly guardando = signal(false);

  protected readonly filtro = this.fb.nonNullable.group({ asignacion: 0, periodoId: 0 });

  /** Notas editadas y aún sin guardar: `matriculaId|criterioId` → valor. */
  protected readonly editadas = signal<Record<string, number | null>>({});
  protected readonly hayCambios = computed(() => Object.keys(this.editadas()).length > 0);
  protected readonly cerrado = computed(() => this.registro()?.estadoPeriodo === 'CERRADO');

  // criterio
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Criterio | null>(null);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly formCriterio = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    peso: [0, [Validators.required, Validators.min(1), Validators.max(100)]],
    origenTipo: ['MANUAL' as OrigenCriterio, Validators.required]
  });

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c => {
        this.ctx.set(c);
        const elegida = c.asignaciones.find(a => a.seccionId === this.seccionId() && a.cursoId === this.cursoId()) ?? c.asignaciones[0];
        this.filtro.patchValue({ asignacion: elegida?.id ?? 0, periodoId: c.periodoActivo?.id ?? 0 });
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo cargar tu información de docente.'))
    });
  }

  protected asignacion(): Asignacion | undefined {
    return this.ctx()?.asignaciones.find(a => a.id === this.filtro.controls.asignacion.value);
  }

  protected cargar(): void {
    const a = this.asignacion();
    const periodoId = this.filtro.controls.periodoId.value;
    this.editadas.set({});
    if (!a || !periodoId) {
      this.registro.set(null);
      return;
    }
    this.cargando.set(true);
    this.api.registro(a.seccionId, a.cursoId, periodoId).subscribe({
      next: r => {
        this.registro.set(r);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.registro.set(null);
        this.avisos.error(mensajeError(e, 'No se pudo cargar el registro de notas.'));
      }
    });
    this.api.validarPesos(periodoId, a.seccionId, a.cursoId).subscribe({ next: p => this.pesos.set(p), error: () => this.pesos.set(null) });
    this.api.pendientes(a.seccionId, a.cursoId).subscribe({ next: p => this.pendientes.set(p), error: () => this.pendientes.set([]) });
  }

  // --- notas ---
  protected valor(matriculaId: number, criterioId: number, original?: number | null): number | null {
    const clave = `${matriculaId}|${criterioId}`;
    return clave in this.editadas() ? this.editadas()[clave] : (original ?? null);
  }

  protected editar(matriculaId: number, criterioId: number, texto: string): void {
    const n = texto === '' ? null : Number(texto);
    this.editadas.update(m => ({ ...m, [`${matriculaId}|${criterioId}`]: n }));
  }

  protected editable(c: Criterio): boolean {
    return c.origenTipo === 'MANUAL' && !this.cerrado();
  }

  protected guardarNotas(): void {
    const r = this.registro();
    const a = this.asignacion();
    if (!r || !a) return;
    const porCriterio = new Map<number, { matriculaId: number; alumnoId: number; valor: number }[]>();
    for (const [clave, valor] of Object.entries(this.editadas())) {
      if (valor === null) continue;
      if (valor < 0 || valor > 20) {
        this.avisos.error('Las notas van de 0 a 20.');
        return;
      }
      const [mid, cid] = clave.split('|').map(Number);
      const alumno = r.alumnos.find(x => x.matriculaId === mid);
      if (!alumno) continue;
      porCriterio.set(cid, [...(porCriterio.get(cid) ?? []), { matriculaId: mid, alumnoId: alumno.alumnoId, valor }]);
    }
    if (porCriterio.size === 0) return;
    this.guardando.set(true);
    let pendientes = porCriterio.size;
    let fallo: string | null = null;
    for (const [criterioId, notas] of porCriterio) {
      this.api.guardarLote({ seccionId: a.seccionId, cursoId: a.cursoId, periodoId: r.periodoId, criterioId, notas }).subscribe({
        next: () => {
          if (--pendientes === 0) this.terminarGuardado(fallo);
        },
        error: e => {
          fallo = mensajeError(e, 'No se pudieron guardar algunas notas.');
          if (--pendientes === 0) this.terminarGuardado(fallo);
        }
      });
    }
  }

  private terminarGuardado(fallo: string | null): void {
    this.guardando.set(false);
    if (fallo) this.avisos.error(fallo);
    else this.avisos.success('Notas guardadas.');
    this.cargar();
  }

  // --- criterios ---
  protected nuevoCriterio(): void {
    const resto = Math.max(0, 100 - (this.pesos()?.sumaPesos ?? 0));
    this.formCriterio.reset({ nombre: '', peso: resto || 10, origenTipo: 'MANUAL' });
    this.editando.set(null);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected editarCriterio(c: Criterio): void {
    this.formCriterio.reset({ nombre: c.nombre, peso: c.peso, origenTipo: c.origenTipo });
    this.editando.set(c);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected cerrarDialogo(): void {
    this.abierto.set(false);
    this.editando.set(null);
  }

  protected guardarCriterio(): void {
    const a = this.asignacion();
    if (!a || this.formCriterio.invalid) {
      this.formCriterio.markAllAsTouched();
      return;
    }
    const v = this.formCriterio.getRawValue();
    const body = { anioId: this.ctx()!.anio.id, periodoId: this.filtro.controls.periodoId.value, seccionId: a.seccionId, cursoId: a.cursoId, nombre: v.nombre.trim(), peso: v.peso, origenTipo: v.origenTipo };
    const e = this.editando();
    (e ? this.api.actualizarCriterio(e.id, body) : this.api.crearCriterio(body)).subscribe({
      next: () => {
        this.avisos.success(e ? 'Criterio actualizado.' : 'Criterio creado.');
        this.cerrarDialogo();
        this.cargar();
      },
      error: err => this.errorForm.set(mensajeError(err))
    });
  }

  protected async eliminarCriterio(c: Criterio): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar el criterio "${c.nombre}" y sus notas?`, 'Eliminar', true))) return;
    this.api.eliminarCriterio(c.id).subscribe({
      next: () => {
        this.avisos.success('Criterio eliminado.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
