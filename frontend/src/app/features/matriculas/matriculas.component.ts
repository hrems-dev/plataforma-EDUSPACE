import { ActivatedRoute } from '@angular/router';
import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { AlumnoResumen } from '../../core/models/alumno.model';
import { Anio, Grado, Seccion } from '../../core/models/planificacion.model';
import {
  ESTADOS_MATRICULA,
  EstadoMatricula,
  Matricula,
  MatriculaDetalle,
  RatificacionResultado,
  Requisito,
  TIPOS_MATRICULA,
  TipoMatricula
} from '../../core/models/matricula.model';
import { AlumnoService } from '../../core/services/alumno.service';
import { MatriculaService } from '../../core/services/matricula.service';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

type Dialogo = 'ninguno' | 'nueva' | 'detalle' | 'motivo' | 'traslado' | 'ratificacion';
type AccionMotivo = 'anular' | 'retirar';

const CLASE_ESTADO: Record<EstadoMatricula, string> = {
  PRE_MATRICULA: 'badge-warn', MATRICULADO: 'badge-ok', RETIRADO: 'badge-danger', TRASLADADO: 'badge-violet', ANULADO: 'badge-gray'
};

@Component({
  selector: 'app-matriculas',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './matriculas.component.html'
})
export class MatriculasComponent implements OnInit {
  private readonly api = inject(MatriculaService);
  private readonly alumnosApi = inject(AlumnoService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly estados = ESTADOS_MATRICULA;
  protected readonly tipos = TIPOS_MATRICULA;
  protected readonly anios = signal<Anio[]>([]);
  protected readonly grados = signal<Grado[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);

  protected readonly matriculas = signal<Matricula[]>([]);
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);

  protected readonly dialogo = signal<Dialogo>('ninguno');
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly seleccionada = signal<Matricula | null>(null);
  protected readonly detalle = signal<MatriculaDetalle | null>(null);
  protected readonly accionMotivo = signal<AccionMotivo>('anular');
  protected readonly resultadoRatificacion = signal<RatificacionResultado | null>(null);

  protected readonly filtro = this.fb.nonNullable.group({ anioId: 0, gradoId: 0, seccionId: 0, estado: '' as EstadoMatricula | '', texto: '' });

  // nueva matrícula
  protected readonly busquedaAlumno = this.fb.nonNullable.control('');
  protected readonly resultadosAlumno = signal<AlumnoResumen[]>([]);
  protected readonly alumnoElegido = signal<AlumnoResumen | null>(null);
  protected readonly formNueva = this.fb.nonNullable.group({
    seccionId: [0, [Validators.required, Validators.min(1)]],
    tipo: ['NUEVA' as TipoMatricula, Validators.required],
    observaciones: ['']
  });

  protected readonly formMotivo = this.fb.nonNullable.group({ motivo: ['', [Validators.required, Validators.maxLength(255)]] });
  protected readonly formTraslado = this.fb.nonNullable.group({
    nuevaSeccionId: [0, [Validators.required, Validators.min(1)]],
    motivo: ['', [Validators.required, Validators.maxLength(255)]]
  });
  protected readonly formRatificacion = this.fb.nonNullable.group({
    anioDestinoId: [0, [Validators.required, Validators.min(1)]],
    seccionOrigenId: [0, [Validators.required, Validators.min(1)]],
    seccionDestinoId: [0]
  });
  protected readonly seccionesOrigen = signal<Seccion[]>([]);
  protected readonly seccionesDestino = signal<Seccion[]>([]);

  ngOnInit(): void {
    this.plan.grados().subscribe(g => this.grados.set(g));
    this.plan.anios().subscribe(a => {
      this.anios.set(a);
      const activo = a.find(x => x.estado === 'ACTIVO') ?? a[0];
      const q = this.ruta.snapshot.queryParamMap;
      // Enlaces desde el inicio: ?estado=PRE_MATRICULA filtra la lista y ?nueva=1 abre el formulario.
      this.filtro.patchValue({ anioId: activo?.id ?? 0, ...(q.get('estado') ? { estado: q.get('estado') as EstadoMatricula } : {}) });
      this.alCambiarAnio();
      if (q.get('nueva')) this.nueva();
    });
  }

  protected clase(e: EstadoMatricula): string {
    return CLASE_ESTADO[e];
  }

  protected nombreSeccion(id: number): string {
    const s = this.secciones().find(x => x.id === id);
    return s ? `${s.gradoNombre} · ${s.nombre}` : `Sección ${id}`;
  }

  // --- lista ---
  protected alCambiarAnio(): void {
    const anioId = this.filtro.controls.anioId.value;
    this.filtro.patchValue({ gradoId: 0, seccionId: 0 });
    this.plan.secciones(anioId || undefined).subscribe(s => this.secciones.set(s));
    this.cargar(0);
  }

  protected seccionesFiltradas(): Seccion[] {
    const g = this.filtro.controls.gradoId.value;
    return g ? this.secciones().filter(s => s.gradoId === g) : this.secciones();
  }

  protected alCambiarGrado(): void {
    this.filtro.patchValue({ seccionId: 0 });
    this.cargar(0);
  }

  protected buscar(): void {
    this.cargar(0);
  }

  protected irA(p: number): void {
    if (p >= 0 && p < this.paginas()) this.cargar(p);
  }

  protected cargar(pagina = this.pagina()): void {
    const f = this.filtro.getRawValue();
    this.cargando.set(true);
    this.api
      .listar({ anioId: f.anioId || undefined, gradoId: f.gradoId || undefined, seccionId: f.seccionId || undefined, estado: f.estado, texto: f.texto.trim() || undefined, page: pagina })
      .subscribe({
        next: p => {
          this.matriculas.set(p.contenido);
          this.total.set(p.totalElementos);
          this.paginas.set(Math.max(1, p.totalPaginas));
          this.pagina.set(p.pagina);
          this.cargando.set(false);
        },
        error: e => {
          this.cargando.set(false);
          this.avisos.error(mensajeError(e, 'No se pudo cargar la lista de matrículas.'));
        }
      });
  }

  // --- diálogos ---
  protected cerrar(): void {
    this.dialogo.set('ninguno');
    this.errorForm.set(null);
    this.seleccionada.set(null);
    this.detalle.set(null);
    this.resultadoRatificacion.set(null);
  }

  private abrir(d: Dialogo): void {
    this.errorForm.set(null);
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

  // --- nueva matrícula ---
  protected nueva(): void {
    this.formNueva.reset({ seccionId: 0, tipo: 'NUEVA', observaciones: '' });
    this.alumnoElegido.set(null);
    this.resultadosAlumno.set([]);
    this.busquedaAlumno.setValue('');
    this.abrir('nueva');
  }

  protected buscarAlumno(): void {
    const q = this.busquedaAlumno.value.trim();
    if (q.length < 2) {
      this.resultadosAlumno.set([]);
      return;
    }
    this.alumnosApi.listar(q, 'ACTIVO', 0, 8).subscribe({ next: p => this.resultadosAlumno.set(p.contenido), error: e => this.avisos.error(mensajeError(e)) });
  }

  protected elegirAlumno(a: AlumnoResumen): void {
    this.alumnoElegido.set(a);
    this.resultadosAlumno.set([]);
  }

  protected guardarNueva(): void {
    const alumno = this.alumnoElegido();
    if (!alumno || this.formNueva.invalid) {
      this.formNueva.markAllAsTouched();
      this.errorForm.set(!alumno ? 'Elige un alumno de la lista.' : 'Elige la sección.');
      return;
    }
    const v = this.formNueva.getRawValue();
    this.ejecutar(this.api.crear({ alumnoId: alumno.id, seccionId: v.seccionId, tipo: v.tipo, observaciones: v.observaciones.trim() || undefined }), m => {
      this.avisos.success(`Pre-matrícula ${m.codigo} creada. Se confirma al registrarse el pago de la matrícula.`);
      this.cerrar();
      this.cargar(0);
    });
  }

  // --- detalle ---
  protected verDetalle(m: Matricula): void {
    this.seleccionada.set(m);
    this.detalle.set(null);
    this.abrir('detalle');
    this.recargarDetalle(m.id);
  }

  private recargarDetalle(id: number): void {
    this.api.detalle(id).subscribe({
      next: d => this.detalle.set(d),
      error: e => this.errorForm.set(mensajeError(e, 'No se pudo cargar el detalle.'))
    });
  }

  protected alternarRequisito(r: Requisito): void {
    const m = this.seleccionada();
    if (!m) return;
    this.api.marcarRequisito(m.id, r.tipo, !r.cumplido).subscribe({
      next: () => this.recargarDetalle(m.id),
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  // --- anular / retirar ---
  protected pedirMotivo(m: Matricula, accion: AccionMotivo): void {
    this.seleccionada.set(m);
    this.accionMotivo.set(accion);
    this.formMotivo.reset({ motivo: '' });
    this.abrir('motivo');
  }

  protected guardarMotivo(): void {
    const m = this.seleccionada();
    if (!m || this.formMotivo.invalid) {
      this.formMotivo.markAllAsTouched();
      return;
    }
    const motivo = this.formMotivo.getRawValue().motivo.trim();
    const peticion = this.accionMotivo() === 'anular' ? this.api.anular(m.id, motivo) : this.api.retirar(m.id, motivo);
    this.ejecutar(peticion, () => {
      this.avisos.success(this.accionMotivo() === 'anular' ? 'Pre-matrícula anulada.' : 'Matrícula retirada.');
      this.cerrar();
      this.cargar();
    });
  }

  // --- traslado ---
  protected pedirTraslado(m: Matricula): void {
    this.seleccionada.set(m);
    this.formTraslado.reset({ nuevaSeccionId: 0, motivo: '' });
    this.abrir('traslado');
  }

  /** El traslado solo se permite dentro del mismo grado y año. */
  protected seccionesParaTraslado(): Seccion[] {
    const m = this.seleccionada();
    return m ? this.secciones().filter(s => s.gradoId === m.gradoId && s.id !== m.seccionId && s.activo) : [];
  }

  protected guardarTraslado(): void {
    const m = this.seleccionada();
    if (!m || this.formTraslado.invalid) {
      this.formTraslado.markAllAsTouched();
      return;
    }
    const v = this.formTraslado.getRawValue();
    this.ejecutar(this.api.trasladar(m.id, v.nuevaSeccionId, v.motivo.trim()), () => {
      this.avisos.success('Alumno trasladado de sección.');
      this.cerrar();
      this.cargar();
    });
  }

  // --- ratificación masiva ---
  protected ratificar(): void {
    const activo = this.anios().find(a => a.estado === 'ACTIVO');
    const siguiente = this.anios().find(a => a.estado === 'PLANIFICADO' && a.id !== activo?.id);
    this.formRatificacion.reset({ anioDestinoId: siguiente?.id ?? 0, seccionOrigenId: 0, seccionDestinoId: 0 });
    this.resultadoRatificacion.set(null);
    this.seccionesOrigen.set(this.secciones());
    this.cargarSeccionesDestino();
    this.abrir('ratificacion');
  }

  protected cargarSeccionesDestino(): void {
    const id = this.formRatificacion.controls.anioDestinoId.value;
    this.formRatificacion.patchValue({ seccionDestinoId: 0 });
    if (!id) {
      this.seccionesDestino.set([]);
      return;
    }
    this.plan.secciones(id).subscribe(s => this.seccionesDestino.set(s));
  }

  protected guardarRatificacion(): void {
    if (this.formRatificacion.invalid) {
      this.formRatificacion.markAllAsTouched();
      return;
    }
    const v = this.formRatificacion.getRawValue();
    this.ejecutar(
      this.api.ratificar({ anioDestinoId: v.anioDestinoId, seccionOrigenId: v.seccionOrigenId, seccionDestinoId: v.seccionDestinoId || undefined }),
      r => {
        this.resultadoRatificacion.set(r);
        this.avisos.success(`Ratificación: ${r.creadas} de ${r.solicitadas} matrículas creadas.`);
        this.cargar(0);
      }
    );
  }
}
