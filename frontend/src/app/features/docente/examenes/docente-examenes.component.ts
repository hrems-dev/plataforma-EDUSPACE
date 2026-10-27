import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EstadoExamen, Examen, Pregunta, ResultadosExamen } from '../../../core/models/examen.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ContextoDocente, DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { ExamenService } from '../../../core/services/examen.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const CLASE: Record<EstadoExamen, string> = { BORRADOR: 'badge-gray', PUBLICADO: 'badge-ok', CERRADO: 'badge-warn', CALIFICADO: 'badge-violet' };

const aLocal = (iso: string): string => iso.slice(0, 16);
const enLocal = (d: Date): string => new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);

@Component({
  selector: 'app-docente-examenes',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, DecimalPipe, ModalComponent, PageHeaderComponent],
  templateUrl: './docente-examenes.component.html'
})
export class DocenteExamenesComponent implements OnInit {
  private readonly api = inject(ExamenService);
  private readonly contexto = inject(DocenteContextoService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly ctx = signal<ContextoDocente | null>(null);
  protected readonly examenes = signal<Examen[]>([]);
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);
  protected readonly estado = this.fb.nonNullable.control<EstadoExamen | ''>('');

  // crear / editar
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Examen | null>(null);
  protected readonly banco = signal<Pregunta[]>([]);
  /** preguntaId → puntaje elegido para este examen. */
  protected readonly elegidas = signal<Record<number, number>>({});
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    asignacion: [0, [Validators.required, Validators.min(1)]],
    periodoId: [0, [Validators.required, Validators.min(1)]],
    titulo: ['', [Validators.required, Validators.maxLength(150)]],
    instrucciones: [''],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required],
    duracionMin: [40, [Validators.required, Validators.min(1)]],
    intentosMax: [1, [Validators.required, Validators.min(1)]],
    barajarPreguntas: [true]
  });

  protected readonly puntajeTotal = computed(() => Object.values(this.elegidas()).reduce((s, n) => s + n, 0));
  protected readonly nElegidas = computed(() => Object.keys(this.elegidas()).length);

  // resultados
  protected readonly resultados = signal<ResultadosExamen | null>(null);
  protected readonly pendientesRevision = signal(0);

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c => {
        this.ctx.set(c);
        this.cargar(0);
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo cargar tu información de docente.'))
    });
  }

  protected clase(e: EstadoExamen): string {
    return CLASE[e];
  }

  protected etiqueta(x: Examen): string {
    const a = this.ctx()?.asignaciones.find(y => y.seccionId === x.seccionId && y.cursoId === x.cursoId);
    return a ? `${a.cursoNombre} · ${a.seccionNombre}` : `Sección ${x.seccionId}`;
  }

  protected irA(p: number): void {
    if (p >= 0 && p < this.paginas()) this.cargar(p);
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.api.listar({ estado: this.estado.value }, pagina).subscribe({
      next: p => {
        this.examenes.set(p.contenido);
        this.total.set(p.totalElementos);
        this.paginas.set(Math.max(1, p.totalPaginas));
        this.pagina.set(p.pagina);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los exámenes.'));
      }
    });
  }

  // --- crear / editar ---
  protected nuevo(): void {
    const c = this.ctx();
    const inicio = new Date(Date.now() + 86_400_000);
    inicio.setHours(8, 0, 0, 0);
    const fin = new Date(inicio.getTime() + 4 * 3_600_000);
    this.form.reset({
      asignacion: c?.asignaciones[0]?.id ?? 0, periodoId: c?.periodoActivo?.id ?? 0, titulo: '', instrucciones: '',
      fechaInicio: enLocal(inicio), fechaFin: enLocal(fin), duracionMin: 40, intentosMax: 1, barajarPreguntas: true
    });
    this.form.controls.asignacion.enable();
    this.elegidas.set({});
    this.editando.set(null);
    this.errorForm.set(null);
    this.cargarBanco();
    this.abierto.set(true);
  }

  protected editar(x: Examen): void {
    const a = this.ctx()?.asignaciones.find(y => y.seccionId === x.seccionId && y.cursoId === x.cursoId);
    this.form.reset({
      asignacion: a?.id ?? 0, periodoId: x.periodoId, titulo: x.titulo, instrucciones: x.instrucciones ?? '',
      fechaInicio: aLocal(x.fechaInicio), fechaFin: aLocal(x.fechaFin), duracionMin: x.duracionMin, intentosMax: x.intentosMax, barajarPreguntas: x.barajarPreguntas
    });
    this.form.controls.asignacion.disable();
    this.editando.set(x);
    this.errorForm.set(null);
    this.cargarBanco();
    this.api.detalle(x.id).subscribe(d => this.elegidas.set(Object.fromEntries(d.preguntas.map(p => [p.preguntaId, p.puntaje]))));
    this.abierto.set(true);
  }

  protected cargarBanco(): void {
    const a = this.ctx()?.asignaciones.find(y => y.id === this.form.controls.asignacion.value);
    if (!a) {
      this.banco.set([]);
      return;
    }
    this.elegidas.set({});
    this.api.preguntas(a.cursoId, undefined, undefined, 0, 100).subscribe({
      next: p => this.banco.set(p.contenido.filter(x => x.activa)),
      error: e => this.avisos.error(mensajeError(e, 'No se pudo cargar el banco de preguntas.'))
    });
  }

  protected estaElegida(p: Pregunta): boolean {
    return p.id in this.elegidas();
  }

  protected alternar(p: Pregunta, marcada: boolean): void {
    this.elegidas.update(m => {
      const n = { ...m };
      if (marcada) n[p.id] = p.puntaje;
      else delete n[p.id];
      return n;
    });
  }

  protected cerrar(): void {
    this.abierto.set(false);
    this.editando.set(null);
  }

  protected guardar(): void {
    const v = this.form.getRawValue();
    const a = this.ctx()?.asignaciones.find(y => y.id === v.asignacion);
    if (this.form.invalid || !a) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.nElegidas() === 0) {
      this.errorForm.set('Elige al menos una pregunta del banco.');
      return;
    }
    if (v.fechaFin <= v.fechaInicio) {
      this.errorForm.set('La ventana debe terminar después de empezar.');
      return;
    }
    const preguntas = Object.entries(this.elegidas()).map(([id, puntaje], i) => ({ preguntaId: Number(id), orden: i + 1, puntaje }));
    const body = {
      anioId: this.ctx()!.anio.id, periodoId: v.periodoId, seccionId: a.seccionId, cursoId: a.cursoId, titulo: v.titulo.trim(),
      instrucciones: v.instrucciones.trim() || undefined, fechaInicio: v.fechaInicio + ':00', fechaFin: v.fechaFin + ':00',
      duracionMin: v.duracionMin, intentosMax: v.intentosMax, barajarPreguntas: v.barajarPreguntas, preguntas
    };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizar(e.id, body) : this.api.crear(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Examen actualizado.' : 'Examen creado como borrador. Publícalo cuando esté listo.');
        this.cerrar();
        this.cargar(this.pagina());
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async publicar(x: Examen): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Publicar "${x.titulo}"? Ya no podrás cambiar sus preguntas y los alumnos reciben un aviso.`, 'Publicar'))) return;
    this.api.publicar(x.id).subscribe({
      next: () => {
        this.avisos.success('Examen publicado.');
        this.cargar(this.pagina());
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected async cerrarExamen(x: Examen): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Cerrar "${x.titulo}" ahora? Los intentos en curso se envían y se califican.`, 'Cerrar examen', true))) return;
    this.api.cerrar(x.id).subscribe({
      next: () => {
        this.avisos.success('Examen cerrado.');
        this.cargar(this.pagina());
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  // --- resultados ---
  protected verResultados(x: Examen): void {
    this.resultados.set(null);
    this.pendientesRevision.set(0);
    this.api.resultados(x.id).subscribe({
      next: r => this.resultados.set(r),
      error: e => this.avisos.error(mensajeError(e, 'No se pudieron cargar los resultados.'))
    });
    this.api.pendientesRevision(x.id).subscribe({ next: p => this.pendientesRevision.set(p.length), error: () => undefined });
  }

  protected cerrarResultados(): void {
    this.resultados.set(null);
  }
}
