import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { EstadoAsistencia, ItemRegistro, Sesion, TipoSesion } from '../../../core/models/asistencia.model';
import { Bloque } from '../../../core/models/planificacion.model';
import { AsistenciaService } from '../../../core/services/asistencia.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ContextoDocente, DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

interface FilaAsistencia {
  matriculaId: number;
  alumnoNombre: string;
  estado: EstadoAsistencia;
  minutosTarde: number | null;
  observacion: string;
}

const hoyLocal = (): string => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

@Component({
  selector: 'app-docente-asistencia',
  imports: [ReactiveFormsModule, PageHeaderComponent],
  templateUrl: './docente-asistencia.component.html'
})
export class DocenteAsistenciaComponent implements OnInit {
  private readonly api = inject(AsistenciaService);
  private readonly contexto = inject(DocenteContextoService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  /** Dentro de la página de un curso: la sección y el curso ya vienen elegidos y se ocultan la cabecera y el selector de sección. */
  readonly seccionId = input<number | null>(null);
  readonly cursoId = input<number | null>(null);
  readonly embebido = input(false);

  protected readonly ctx = signal<ContextoDocente | null>(null);
  protected readonly bloques = signal<Bloque[]>([]);
  protected readonly sesion = signal<Sesion | null>(null);
  protected readonly filas = signal<FilaAsistencia[]>([]);
  protected readonly cargando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly cambios = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    seccionId: 0,
    fecha: hoyLocal(),
    tipo: 'DIA' as TipoSesion,
    cursoId: 0,
    bloqueId: 0
  });

  /** Secciones donde dicta (sin repetir). */
  protected readonly secciones = computed(() => {
    const m = new Map<number, string>();
    for (const a of this.ctx()?.asignaciones ?? []) m.set(a.seccionId, a.seccionNombre);
    return [...m].map(([id, nombre]) => ({ id, nombre }));
  });

  protected cursosDeSeccion(): { id: number; nombre: string }[] {
    const s = this.form.controls.seccionId.value;
    return (this.ctx()?.asignaciones ?? []).filter(a => a.seccionId === s).map(a => ({ id: a.cursoId, nombre: a.cursoNombre }));
  }

  protected readonly resumen = computed(() => {
    const f = this.filas();
    const c = (e: EstadoAsistencia) => f.filter(x => x.estado === e).length;
    return { presentes: c('PRESENTE'), tardanzas: c('TARDANZA'), faltas: c('FALTA'), justificados: c('JUSTIFICADO') };
  });

  protected get cerrada(): boolean {
    return this.sesion()?.estado === 'CERRADA';
  }

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c => {
        this.ctx.set(c);
        const primera = c.asignaciones.find(a => a.seccionId === this.seccionId() && a.cursoId === this.cursoId()) ?? c.asignaciones[0];
        this.form.patchValue({ seccionId: primera?.seccionId ?? 0, cursoId: primera?.cursoId ?? 0 });
        this.cargarBloques();
      },
      error: e => this.error.set(mensajeError(e, 'No se pudo cargar tu información de docente.'))
    });
  }

  protected alCambiarSeccion(): void {
    this.sesion.set(null);
    this.filas.set([]);
    this.form.patchValue({ cursoId: this.cursosDeSeccion()[0]?.id ?? 0 });
    this.cargarBloques();
  }

  private cargarBloques(): void {
    const id = this.form.controls.seccionId.value;
    if (!id) return;
    this.plan.horarioSeccion(id).subscribe({
      next: h => {
        this.bloques.set(h.bloques.filter(b => !b.esReceso).sort((a, b) => a.orden - b.orden));
        this.form.patchValue({ bloqueId: this.bloques()[0]?.id ?? 0 });
      },
      error: () => this.bloques.set([])
    });
  }

  // --- abrir y registrar ---
  protected abrir(): void {
    const v = this.form.getRawValue();
    if (!v.seccionId) return;
    if (v.tipo === 'CURSO' && (!v.cursoId || !v.bloqueId)) {
      this.error.set('Para una sesión por curso elige el curso y el bloque.');
      return;
    }
    this.error.set(null);
    this.cargando.set(true);
    const body = { seccionId: v.seccionId, fecha: v.fecha, tipo: v.tipo, ...(v.tipo === 'CURSO' ? { cursoId: v.cursoId, bloqueId: v.bloqueId } : {}) };
    this.api.abrirSesion(body).subscribe({
      next: s => {
        this.cargando.set(false);
        this.mostrar(s);
      },
      error: e => {
        this.cargando.set(false);
        this.sesion.set(null);
        this.filas.set([]);
        this.error.set(mensajeError(e, 'No se pudo abrir la hoja de asistencia.'));
      }
    });
  }

  private mostrar(s: Sesion): void {
    this.sesion.set(s);
    this.filas.set(
      s.alumnos.map(a => ({ matriculaId: a.matriculaId, alumnoNombre: a.alumnoNombre, estado: a.estado, minutosTarde: a.minutosTarde ?? null, observacion: a.observacion ?? '' }))
    );
    this.cambios.set(false);
  }

  protected marcar(f: FilaAsistencia, estado: EstadoAsistencia): void {
    if (this.cerrada || f.estado === 'JUSTIFICADO') return;
    this.filas.update(l => l.map(x => (x.matriculaId === f.matriculaId ? { ...x, estado, minutosTarde: estado === 'TARDANZA' ? (x.minutosTarde ?? 10) : null } : x)));
    this.cambios.set(true);
  }

  protected minutos(f: FilaAsistencia, valor: string): void {
    const n = Number(valor);
    this.filas.update(l => l.map(x => (x.matriculaId === f.matriculaId ? { ...x, minutosTarde: n > 0 ? n : null } : x)));
    this.cambios.set(true);
  }

  protected todosPresentes(): void {
    if (this.cerrada) return;
    this.filas.update(l => l.map(x => (x.estado === 'JUSTIFICADO' ? x : { ...x, estado: 'PRESENTE', minutosTarde: null })));
    this.cambios.set(true);
  }

  protected guardar(): void {
    const s = this.sesion();
    if (!s) return;
    const items: ItemRegistro[] = this.filas().map(f => ({
      matriculaId: f.matriculaId,
      estado: f.estado,
      minutosTarde: f.estado === 'TARDANZA' ? (f.minutosTarde ?? 1) : undefined,
      observacion: f.observacion.trim() || undefined
    }));
    this.guardando.set(true);
    this.api.guardarRegistro(s.id, items).subscribe({
      next: r => {
        this.guardando.set(false);
        this.mostrar(r);
        this.avisos.success('Asistencia guardada. Los apoderados de los alumnos con falta reciben un aviso.');
      },
      error: e => {
        this.guardando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo guardar la asistencia.'));
      }
    });
  }

  protected async cerrarSesion(): Promise<void> {
    const s = this.sesion();
    if (!s) return;
    if (this.cambios()) {
      this.avisos.warning('Guarda los cambios antes de cerrar la hoja.');
      return;
    }
    if (!(await this.confirmacion.confirmar('¿Cerrar la hoja de asistencia? Después solo Secretaría puede corregirla, con un motivo.', 'Cerrar hoja', true))) return;
    this.api.cerrarSesion(s.id).subscribe({
      next: r => {
        this.mostrar(r);
        this.avisos.success('Hoja cerrada.');
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
