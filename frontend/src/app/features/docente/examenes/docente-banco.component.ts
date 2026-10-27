import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Opcion, Pregunta, TIPOS_PREGUNTA, TipoPregunta } from '../../../core/models/examen.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ContextoDocente, DocenteContextoService } from '../../../core/services/docente-contexto.service';
import { ExamenService } from '../../../core/services/examen.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const ETIQUETA: Record<TipoPregunta, string> = {
  OPCION_UNICA: 'Opción única',
  OPCION_MULTIPLE: 'Opción múltiple',
  VERDADERO_FALSO: 'Verdadero / Falso',
  ABIERTA: 'Pregunta abierta'
};

@Component({
  selector: 'app-docente-banco',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './docente-banco.component.html'
})
export class DocenteBancoComponent implements OnInit {
  private readonly api = inject(ExamenService);
  private readonly contexto = inject(DocenteContextoService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly tipos = TIPOS_PREGUNTA;
  protected readonly etiquetaTipo = (t: TipoPregunta) => ETIQUETA[t];
  protected readonly ctx = signal<ContextoDocente | null>(null);
  protected readonly preguntas = signal<Pregunta[]>([]);
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);

  /** Cursos distintos que dicta el docente. */
  protected readonly cursos = computed(() => {
    const m = new Map<number, string>();
    for (const a of this.ctx()?.asignaciones ?? []) m.set(a.cursoId, a.cursoNombre);
    return [...m].map(([id, nombre]) => ({ id, nombre }));
  });

  protected readonly filtro = this.fb.nonNullable.group({ cursoId: 0, tema: '', tipo: '' as TipoPregunta | '' });

  protected readonly abierto = signal(false);
  protected readonly editando = signal<Pregunta | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    cursoId: [0, [Validators.required, Validators.min(1)]],
    tema: [''],
    tipo: ['OPCION_UNICA' as TipoPregunta, Validators.required],
    enunciado: ['', Validators.required],
    puntaje: [2, [Validators.required, Validators.min(0.5)]],
    activa: [true],
    opciones: this.fb.array([])
  });

  protected get opciones(): FormArray {
    return this.form.controls.opciones as unknown as FormArray;
  }

  ngOnInit(): void {
    this.contexto.cargar().subscribe({
      next: c => {
        this.ctx.set(c);
        this.filtro.patchValue({ cursoId: this.cursos()[0]?.id ?? 0 });
        this.cargar(0);
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo cargar tu información de docente.'))
    });
  }

  protected irA(p: number): void {
    if (p >= 0 && p < this.paginas()) this.cargar(p);
  }

  protected cargar(pagina = 0): void {
    const f = this.filtro.getRawValue();
    if (!f.cursoId) return;
    this.cargando.set(true);
    this.api.preguntas(f.cursoId, f.tema.trim() || undefined, f.tipo, pagina).subscribe({
      next: p => {
        this.preguntas.set(p.contenido);
        this.total.set(p.totalElementos);
        this.paginas.set(Math.max(1, p.totalPaginas));
        this.pagina.set(p.pagina);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar el banco de preguntas.'));
      }
    });
  }

  // --- formulario ---
  private opcionForm(texto = '', esCorrecta = false) {
    return this.fb.nonNullable.group({ texto: [texto, Validators.required], esCorrecta: [esCorrecta] });
  }

  private armarOpciones(tipo: TipoPregunta, previas: Opcion[] = []): void {
    this.opciones.clear();
    if (tipo === 'ABIERTA') return;
    if (tipo === 'VERDADERO_FALSO') {
      const v = previas.find(o => o.texto === 'Verdadero')?.esCorrecta ?? true;
      this.opciones.push(this.opcionForm('Verdadero', v));
      this.opciones.push(this.opcionForm('Falso', !v));
      return;
    }
    const base = previas.length ? previas : [{ texto: '', esCorrecta: true }, { texto: '', esCorrecta: false }, { texto: '', esCorrecta: false }];
    for (const o of base) this.opciones.push(this.opcionForm(o.texto, !!o.esCorrecta));
  }

  protected alCambiarTipo(): void {
    this.armarOpciones(this.form.controls.tipo.value);
  }

  protected agregarOpcion(): void {
    this.opciones.push(this.opcionForm());
  }

  protected quitarOpcion(i: number): void {
    if (this.opciones.length > 2) this.opciones.removeAt(i);
  }

  /** En opción única y verdadero/falso solo una opción puede ser la correcta. */
  protected marcarCorrecta(i: number, valor: boolean): void {
    const tipo = this.form.controls.tipo.value;
    if (tipo !== 'OPCION_MULTIPLE' && valor) {
      this.opciones.controls.forEach((c, idx) => c.get('esCorrecta')!.setValue(idx === i, { emitEvent: false }));
    }
  }

  protected nueva(): void {
    this.form.reset({ cursoId: this.filtro.controls.cursoId.value, tema: '', tipo: 'OPCION_UNICA', enunciado: '', puntaje: 2, activa: true });
    this.armarOpciones('OPCION_UNICA');
    this.editando.set(null);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected editar(p: Pregunta): void {
    this.form.reset({ cursoId: p.cursoId, tema: p.tema ?? '', tipo: p.tipo, enunciado: p.enunciado, puntaje: p.puntaje, activa: p.activa });
    this.armarOpciones(p.tipo, [...p.opciones].sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0)));
    this.editando.set(p);
    this.errorForm.set(null);
    this.abierto.set(true);
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
    const opciones = v.opciones as { texto: string; esCorrecta: boolean }[];
    if (v.tipo !== 'ABIERTA' && !opciones.some(o => o.esCorrecta)) {
      this.errorForm.set('Marca al menos una opción correcta.');
      return;
    }
    const body = {
      cursoId: v.cursoId,
      tema: v.tema.trim() || undefined,
      tipo: v.tipo,
      enunciado: v.enunciado.trim(),
      puntaje: v.puntaje,
      activa: v.activa,
      opciones: v.tipo === 'ABIERTA' ? [] : opciones.map((o, i) => ({ texto: o.texto.trim(), esCorrecta: o.esCorrecta, orden: i + 1 }))
    };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarPregunta(e.id, body) : this.api.crearPregunta(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Pregunta actualizada.' : 'Pregunta agregada al banco.');
        this.cerrar();
        this.cargar(this.pagina());
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async eliminar(p: Pregunta): Promise<void> {
    if (!(await this.confirmacion.confirmar('¿Eliminar esta pregunta? Si ya se usó en un examen, solo se desactiva.', 'Eliminar', true))) return;
    this.api.eliminarPregunta(p.id).subscribe({
      next: () => {
        this.avisos.success('Pregunta eliminada.');
        this.cargar(this.pagina());
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
