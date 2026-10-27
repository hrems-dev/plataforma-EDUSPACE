import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { Anio, EstadoPlan, Periodo } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

type Dialogo = 'ninguno' | 'anio' | 'periodo';

const CLASE_ESTADO: Record<EstadoPlan, string> = { PLANIFICADO: 'badge-warn', ACTIVO: 'badge-ok', CERRADO: 'badge-gray' };
const SIGUIENTE: Record<EstadoPlan, EstadoPlan | null> = { PLANIFICADO: 'ACTIVO', ACTIVO: 'CERRADO', CERRADO: null };

@Component({
  selector: 'app-anios',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './anios.component.html'
})
export class AniosComponent implements OnInit {
  private readonly api = inject(PlanificacionService);

  /** En planificación solo el ADMIN escribe (horarios, asignaciones y docentes también los edita SECRETARIA). */
  protected readonly puedeEditar = inject(AuthService).isAdmin;
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly anios = signal<Anio[]>([]);
  protected readonly periodos = signal<Periodo[]>([]);
  protected readonly anioSel = signal<Anio | null>(null);
  protected readonly dialogo = signal<Dialogo>('ninguno');
  protected readonly editando = signal<Anio | Periodo | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly formAnio = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(20)]],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required]
  });

  protected readonly formPeriodo = this.fb.nonNullable.group({
    numero: [1, [Validators.required, Validators.min(1)]],
    nombre: ['', Validators.required],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required]
  });

  ngOnInit(): void {
    this.cargarAnios();
  }

  protected clase(e: EstadoPlan): string {
    return CLASE_ESTADO[e];
  }

  protected siguiente(e: EstadoPlan): EstadoPlan | null {
    return SIGUIENTE[e];
  }

  private cargarAnios(seleccionarId?: number): void {
    this.api.anios().subscribe({
      next: lista => {
        this.anios.set(lista);
        const actual = lista.find(a => a.id === (seleccionarId ?? this.anioSel()?.id)) ?? lista.find(a => a.estado === 'ACTIVO') ?? lista[0] ?? null;
        this.seleccionar(actual);
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudieron cargar los años.'))
    });
  }

  protected seleccionar(a: Anio | null): void {
    this.anioSel.set(a);
    this.periodos.set([]);
    if (a) {
      this.api.periodos(a.id).subscribe({
        next: p => this.periodos.set(p),
        error: e => this.avisos.error(mensajeError(e, 'No se pudieron cargar los periodos.'))
      });
    }
  }

  // --- Años ---
  protected nuevoAnio(): void {
    this.formAnio.reset({ nombre: String(new Date().getFullYear() + 1), fechaInicio: '', fechaFin: '' });
    this.abrir('anio', null);
  }

  protected editarAnio(a: Anio): void {
    this.formAnio.reset({ nombre: a.nombre, fechaInicio: a.fechaInicio, fechaFin: a.fechaFin });
    this.abrir('anio', a);
  }

  protected guardarAnio(): void {
    if (this.formAnio.invalid) {
      this.formAnio.markAllAsTouched();
      return;
    }
    const body = this.formAnio.getRawValue();
    const e = this.editando() as Anio | null;
    this.ejecutar(e ? this.api.actualizarAnio(e.id, body) : this.api.crearAnio(body), a => {
      this.avisos.success(e ? 'Año actualizado.' : 'Año creado.');
      this.cerrar();
      this.cargarAnios(a.id);
    });
  }

  protected async activarAnio(a: Anio): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Activar el año ${a.nombre}? El año activo actual dejará de serlo.`, 'Activar'))) return;
    this.api.activarAnio(a.id).subscribe({
      next: () => {
        this.avisos.success(`Año ${a.nombre} activado.`);
        this.cargarAnios(a.id);
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected async cerrarAnio(a: Anio): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Cerrar el año ${a.nombre}? Esta acción termina el ciclo académico.`, 'Cerrar año', true))) return;
    this.api.cerrarAnio(a.id).subscribe({
      next: () => {
        this.avisos.success(`Año ${a.nombre} cerrado.`);
        this.cargarAnios(a.id);
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  // --- Periodos ---
  protected nuevoPeriodo(): void {
    const n = this.periodos().length + 1;
    const anio = this.anioSel();
    this.formPeriodo.reset({ numero: n, nombre: `Bimestre ${n}`, fechaInicio: anio?.fechaInicio ?? '', fechaFin: '' });
    this.abrir('periodo', null);
  }

  protected editarPeriodo(p: Periodo): void {
    this.formPeriodo.reset({ numero: p.numero, nombre: p.nombre, fechaInicio: p.fechaInicio, fechaFin: p.fechaFin });
    this.abrir('periodo', p);
  }

  protected guardarPeriodo(): void {
    const anio = this.anioSel();
    if (this.formPeriodo.invalid || !anio) {
      this.formPeriodo.markAllAsTouched();
      return;
    }
    const body = { anioId: anio.id, ...this.formPeriodo.getRawValue() };
    const e = this.editando() as Periodo | null;
    this.ejecutar(e ? this.api.actualizarPeriodo(e.id, body) : this.api.crearPeriodo(body), () => {
      this.avisos.success(e ? 'Periodo actualizado.' : 'Periodo creado.');
      this.cerrar();
      this.seleccionar(anio);
    });
  }

  protected avanzarPeriodo(p: Periodo): void {
    const sig = SIGUIENTE[p.estado];
    if (!sig) return;
    this.api.cambiarEstadoPeriodo(p.id, sig).subscribe({
      next: () => {
        this.avisos.success(`Periodo ${p.nombre}: ${sig.toLowerCase()}.`);
        this.seleccionar(this.anioSel());
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected async eliminarPeriodo(p: Periodo): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar el periodo ${p.nombre}?`, 'Eliminar', true))) return;
    this.api.eliminarPeriodo(p.id).subscribe({
      next: () => {
        this.avisos.success('Periodo eliminado.');
        this.seleccionar(this.anioSel());
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  // --- comunes ---
  protected cerrar(): void {
    this.dialogo.set('ninguno');
    this.editando.set(null);
    this.errorForm.set(null);
  }

  private abrir(d: Dialogo, e: Anio | Periodo | null): void {
    this.errorForm.set(null);
    this.editando.set(e);
    this.dialogo.set(d);
  }

  private ejecutar<T>(obs: Observable<T>, ok: (r: T) => void): void {
    this.guardando.set(true);
    this.errorForm.set(null);
    obs.subscribe({
      next: r => {
        this.guardando.set(false);
        ok(r);
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }
}
