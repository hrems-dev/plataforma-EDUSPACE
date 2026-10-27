import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { interval } from 'rxjs';
import { ExamenDisponible, IntentoIniciado, IntentoResultado, PreguntaEstudiante } from '../../../core/models/examen.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ExamenService } from '../../../core/services/examen.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

interface Respuesta {
  opciones: number[];
  texto: string;
}

type Fase = 'inicio' | 'rindiendo' | 'enviado';

@Component({
  selector: 'app-rendir-examen',
  imports: [RouterLink, PageHeaderComponent],
  templateUrl: './rendir-examen.component.html',
  styleUrl: './rendir-examen.component.css'
})
export class RendirExamenComponent {
  private readonly api = inject(ExamenService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly router = inject(Router);

  /** Parámetro de ruta `:id` = id del examen. */
  readonly id = input.required<string>();

  protected readonly fase = signal<Fase>('inicio');
  protected readonly examen = signal<ExamenDisponible | null>(null);
  protected readonly intento = signal<IntentoIniciado | null>(null);
  protected readonly resultado = signal<IntentoResultado | null>(null);
  protected readonly respuestas = signal<Record<number, Respuesta>>({});
  protected readonly guardadas = signal<Set<number>>(new Set());
  protected readonly cargando = signal(true);
  protected readonly iniciando = signal(false);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  // Reloj: solo muestra lo que queda según `venceEn`; quien cierra el intento a la hora es el servidor.
  private readonly ahora = signal(Date.now());
  protected readonly restanteSeg = computed(() => {
    const i = this.intento();
    if (!i) return 0;
    return Math.max(0, Math.floor((Date.parse(i.venceEn) - this.ahora()) / 1000));
  });
  protected readonly reloj = computed(() => {
    const s = this.restanteSeg();
    return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  });
  protected readonly poco = computed(() => this.restanteSeg() <= 300);
  protected readonly preguntas = computed(() => [...(this.intento()?.preguntas ?? [])].sort((a, b) => a.orden - b.orden));
  protected readonly contestadas = computed(() => this.preguntas().filter(p => this.estaRespondida(p)).length);

  constructor() {
    interval(1000)
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => {
        this.ahora.set(Date.now());
        if (this.fase() === 'rindiendo' && this.restanteSeg() === 0 && !this.enviando()) this.enviarAutomatico();
      });
    queueMicrotask(() => this.cargarExamen());
  }

  private cargarExamen(): void {
    this.api.disponibles().subscribe({
      next: d => {
        this.examen.set(d.find(e => e.examenId === Number(this.id())) ?? null);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No se pudo cargar el examen.'));
      }
    });
  }

  // --- empezar ---
  protected comenzar(): void {
    this.iniciando.set(true);
    this.error.set(null);
    this.api.iniciarIntento(Number(this.id())).subscribe({
      next: i => {
        this.iniciando.set(false);
        this.intento.set(i);
        const previas: Record<number, Respuesta> = {};
        for (const p of i.preguntas) previas[p.preguntaId] = { opciones: p.opcionesRespondidas ?? [], texto: p.textoRespondido ?? '' };
        this.respuestas.set(previas);
        this.guardadas.set(new Set(i.preguntas.filter(p => (p.opcionesRespondidas?.length ?? 0) > 0 || p.textoRespondido).map(p => p.preguntaId)));
        this.fase.set('rindiendo');
      },
      error: e => {
        this.iniciando.set(false);
        this.error.set(mensajeError(e, 'No se pudo iniciar el examen.'));
      }
    });
  }

  // --- responder ---
  protected estaRespondida(p: PreguntaEstudiante): boolean {
    const r = this.respuestas()[p.preguntaId];
    return !!r && (r.opciones.length > 0 || r.texto.trim().length > 0);
  }

  protected marcada(p: PreguntaEstudiante, opcionId: number): boolean {
    return this.respuestas()[p.preguntaId]?.opciones.includes(opcionId) ?? false;
  }

  protected elegir(p: PreguntaEstudiante, opcionId: number, marcada: boolean): void {
    const actual = this.respuestas()[p.preguntaId]?.opciones ?? [];
    const opciones = p.tipo === 'OPCION_MULTIPLE' ? (marcada ? [...new Set([...actual, opcionId])] : actual.filter(x => x !== opcionId)) : [opcionId];
    this.respuestas.update(r => ({ ...r, [p.preguntaId]: { ...(r[p.preguntaId] ?? { texto: '' }), opciones } }));
    this.guardar(p, { opcionesIds: opciones });
  }

  protected escribir(p: PreguntaEstudiante, texto: string): void {
    this.respuestas.update(r => ({ ...r, [p.preguntaId]: { opciones: r[p.preguntaId]?.opciones ?? [], texto } }));
  }

  protected guardarTexto(p: PreguntaEstudiante): void {
    this.guardar(p, { textoRespuesta: this.respuestas()[p.preguntaId]?.texto ?? '' });
  }

  /** Guardado incremental: cada respuesta se envía apenas cambia, así no se pierde nada si se corta la conexión. */
  private guardar(p: PreguntaEstudiante, cuerpo: { opcionesIds?: number[]; textoRespuesta?: string }): void {
    const i = this.intento();
    if (!i) return;
    this.api.guardarRespuesta(i.intentoId, p.preguntaId, cuerpo).subscribe({
      next: () => this.guardadas.update(s => new Set(s).add(p.preguntaId)),
      error: e => this.avisos.error(mensajeError(e, 'No se pudo guardar tu respuesta. Revisa tu conexión.'))
    });
  }

  // --- enviar ---
  protected async enviar(): Promise<void> {
    const faltan = this.preguntas().length - this.contestadas();
    const aviso = faltan > 0 ? `Te faltan ${faltan} pregunta(s) sin responder. Al enviar ya no podrás cambiar nada. ¿Enviar el examen?` : 'Al enviar ya no podrás cambiar tus respuestas. ¿Enviar el examen?';
    if (!(await this.confirmacion.confirmar(aviso, 'Enviar examen'))) return;
    this.mandar();
  }

  private enviarAutomatico(): void {
    this.avisos.warning('Se acabó el tiempo: tu examen se envió automáticamente.');
    this.mandar();
  }

  private mandar(): void {
    const i = this.intento();
    if (!i || this.enviando()) return;
    this.enviando.set(true);
    this.api.enviar(i.intentoId).subscribe({
      next: r => {
        this.enviando.set(false);
        this.resultado.set(r);
        this.fase.set('enviado');
      },
      error: e => {
        this.enviando.set(false);
        this.error.set(mensajeError(e, 'No se pudo enviar el examen.'));
      }
    });
  }

  protected volver(): void {
    this.router.navigate(['/familia/examenes']);
  }
}
