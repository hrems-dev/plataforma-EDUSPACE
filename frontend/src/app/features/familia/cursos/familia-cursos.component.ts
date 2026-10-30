import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cuota } from '../../../core/models/cobro.model';
import { DIAS_SEMANA } from '../../../core/models/planificacion.model';
import { CobroService } from '../../../core/services/cobro.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { nombreNatural } from '../../../shared/aula/nombres';
import { CursoTarjetaComponent } from '../../../shared/aula/curso-tarjeta.component';
import { FamiliaDatosService } from './familia-datos.service';

const MESES = (iso: string): string => new Date(iso).toLocaleDateString('es-PE', { weekday: 'short', day: 'numeric', month: 'short' });

/** «Mis cursos»: los cursos del alumno como tarjetas; al elegir uno se ven sus tareas, notas y exámenes. */
@Component({
  selector: 'app-familia-cursos',
  imports: [RouterLink, CursoTarjetaComponent],
  template: `
    <section class="hero">
      <div>
        <p class="hola">{{ saludo() }}</p>
        <h1>{{ nombre(ctx.seleccionado()?.nombreCompleto) || 'Mis cursos' }}</h1>
        <p class="sub">{{ resumen() }}</p>
      </div>
      @if (ctx.hijos().length > 1) {
        <div class="hijos" role="group" aria-label="Elegir hijo">
          @for (h of ctx.hijos(); track h.alumnoId) {
            <button type="button" class="hijo" [class.on]="ctx.seleccionado()?.alumnoId === h.alumnoId" (click)="ctx.seleccionar(h.alumnoId)">
              <span class="ini" aria-hidden="true">{{ inicial(h.nombreCompleto) }}</span>{{ primerNombre(h.nombreCompleto) }}
            </button>
          }
        </div>
      }
    </section>

    @if (error()) { <div class="aviso aviso-warn" role="alert">{{ error() }}</div> }

    @if (vencidas().length > 0) {
      <a class="alerta-pago" routerLink="/familia/pagos">
        <strong>{{ vencidas().length }} cuota(s) vencida(s)</strong>
        <span>Regularizar el pago →</span>
      </a>
    }

    <div class="columnas">
      <section aria-labelledby="t-cursos">
        <h2 id="t-cursos">Cursos <span class="cuenta">{{ datos.cursos().length }}</span></h2>
        @if (datos.cargando()) {
          <div class="rejilla">@for (i of [1, 2, 3, 4]; track i) { <div class="esqueleto"></div> }</div>
        } @else if (datos.cursos().length === 0) {
          <div class="vacio">
            <strong>Todavía no hay cursos para mostrar</strong>
            <span>Aparecen cuando la matrícula está confirmada y el colegio define los cursos del grado.</span>
          </div>
        } @else {
          <div class="rejilla">
            @for (c of datos.cursos(); track c.cursoId) {
              <app-curso-tarjeta [cursoId]="c.cursoId" [nombre]="c.nombre" [ruta]="['/familia/curso', c.cursoId]"
                [subtitulo]="c.docente ?? 'Docente por asignar'"
                [datos]="datosDe(c)" [insignia]="c.pendientes ? c.pendientes + (c.pendientes === 1 ? ' tarea' : ' tareas') : ''" [alerta]="c.pendientes > 0" />
            }
          </div>
        }
      </section>

      <aside class="lateral">
        <section class="caja" aria-labelledby="t-hoy">
          <h2 id="t-hoy">Hoy · {{ nombreDia }}</h2>
          @for (k of clasesHoy(); track k.id) {
            <div class="clase"><span class="hora">{{ k.horaInicio.slice(0, 5) }}</span><span><strong>{{ k.cursoNombre }}</strong><br /><small>{{ k.docenteNombre }}</small></span></div>
          } @empty {
            <p class="muted">{{ hoy === 0 || hoy === 6 ? 'Hoy no hay clases.' : 'No hay clases programadas hoy.' }}</p>
          }
          <a class="enlace" routerLink="/familia/horario">Ver horario completo →</a>
        </section>

        <section class="caja" aria-labelledby="t-prox">
          <h2 id="t-prox">Próximas entregas</h2>
          @for (t of proximas(); track t.tareaId) {
            <a class="entrega" [routerLink]="['/familia/curso', t.cursoId]" [queryParams]="{ ver: 'tareas' }">
              <strong>{{ t.titulo }}</strong>
              <small [class.vencida]="t.vencida">{{ nombreCurso(t.cursoId) }} · {{ t.vencida ? 'vencida' : fecha(t.fechaEntrega) }}</small>
            </a>
          } @empty {
            <p class="muted">No hay tareas pendientes. 🎉</p>
          }
        </section>
      </aside>
    </div>
  `,
  styles: `
    .hero { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
    .hola { margin: 0; color: var(--primary); font-weight: 700; font-size: .9rem; }
    h1 { margin: 2px 0 4px; font-size: 1.9rem; letter-spacing: -.02em; }
    .sub { margin: 0; color: var(--ink-2); }
    .hijos { display: flex; gap: 8px; flex-wrap: wrap; }
    .hijo { display: inline-flex; align-items: center; gap: 8px; padding: 6px 14px 6px 6px; border-radius: 999px; border: 1px solid var(--line); background: var(--card); font: inherit; font-weight: 600; cursor: pointer; color: var(--ink-2); }
    .hijo.on { background: var(--primary); border-color: var(--primary); color: #fff; }
    .ini { width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; background: var(--primary-soft); color: var(--primary); font-size: .8rem; font-weight: 800; }
    .hijo.on .ini { background: rgb(255 255 255 / .25); color: #fff; }
    .alerta-pago { display: flex; justify-content: space-between; gap: 12px; padding: 12px 16px; margin-bottom: 18px; border-radius: 14px; background: var(--danger-soft); color: var(--danger); text-decoration: none; }
    .columnas { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 26px; align-items: start; }
    h2 { font-size: 1.05rem; margin: 0 0 12px; }
    .cuenta { margin-left: 6px; font-size: .8rem; background: var(--primary-soft); color: var(--primary); border-radius: 999px; padding: 1px 9px; }
    .rejilla { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
    .esqueleto { height: 90px; border-radius: 16px; background: linear-gradient(90deg, #eef1f8, #f8f9fd, #eef1f8); background-size: 200% 100%; animation: brillo 1.4s infinite; }
    @keyframes brillo { to { background-position: -200% 0; } }
    .vacio { display: flex; flex-direction: column; gap: 4px; padding: 28px; text-align: center; border: 2px dashed var(--line-2); border-radius: 16px; color: var(--ink-2); }
    .lateral { display: flex; flex-direction: column; gap: 16px; }
    .caja { background: var(--card); border: 1px solid var(--line); border-radius: 16px; padding: 16px; }
    .clase { display: flex; gap: 12px; padding: 8px 0; border-top: 1px solid var(--line); }
    .clase:first-of-type { border-top: 0; }
    .hora { flex: none; width: 46px; font-weight: 700; color: var(--primary); }
    .entrega { display: flex; flex-direction: column; padding: 9px 0; border-top: 1px solid var(--line); text-decoration: none; color: var(--ink); }
    .entrega:first-of-type { border-top: 0; }
    .entrega small { color: var(--ink-2); }
    .entrega small.vencida { color: var(--danger); }
    .enlace { display: inline-block; margin-top: 8px; font-size: .88rem; font-weight: 600; text-decoration: none; }
    @media (max-width: 960px) { .columnas { grid-template-columns: minmax(0, 1fr); } }
    @media (prefers-reduced-motion: reduce) { .esqueleto { animation: none; } }
  `
})
export class FamiliaCursosComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  protected readonly datos = inject(FamiliaDatosService);
  private readonly cobro = inject(CobroService);
  private readonly avisos = inject(NotificationService);

  protected readonly error = signal<string | null>(null);
  protected readonly cuotas = signal<Cuota[]>([]);
  protected readonly hoy = new Date().getDay();
  protected readonly nombreDia = DIAS_SEMANA[this.hoy] ?? 'Fin de semana';

  protected readonly clasesHoy = computed(() => this.datos.clases().filter(c => c.diaSemana === this.hoy).sort((a, b) => a.bloqueOrden - b.bloqueOrden));
  protected readonly proximas = computed(() =>
    this.datos.tareas()
      .filter(t => t.alumnoId === this.ctx.seleccionado()?.alumnoId && t.estadoEntrega === 'SIN_ENTREGAR')
      .sort((a, b) => a.fechaEntrega.localeCompare(b.fechaEntrega))
      .slice(0, 5)
  );
  protected readonly vencidas = computed(() => this.cuotas().filter(c => c.alumnoId === this.ctx.seleccionado()?.alumnoId && (c.estado === 'VENCIDA' || c.diasAtraso > 0)));
  protected readonly saludo = computed(() => {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  });
  protected readonly resumen = computed(() => {
    const p = this.datos.cursos().reduce((s, c) => s + c.pendientes, 0);
    const n = this.datos.cursos().length;
    return n === 0 ? 'Aquí verás sus cursos, tareas y notas.' : `${n} cursos · ${p === 0 ? 'sin tareas pendientes' : p + (p === 1 ? ' tarea por entregar' : ' tareas por entregar')}`;
  });

  ngOnInit(): void {
    this.ctx.cargar().subscribe({ error: e => this.error.set(mensajeError(e, 'No se pudo cargar la información.')) });
    if (this.ctx.esApoderado()) this.cobro.misCuotas().subscribe({ next: c => this.cuotas.set(c), error: () => this.cuotas.set([]) });
  }

  protected datosDe(c: { horas: number; promedio: number | null; literal: string | null; proxima: { fechaEntrega: string } | null }): string[] {
    const d: string[] = [];
    if (c.promedio !== null) d.push(`Promedio ${c.promedio}${c.literal ? ' (' + c.literal + ')' : ''}`);
    if (c.proxima) d.push(`Entrega ${fechaCorta(c.proxima.fechaEntrega)}`);
    if (c.horas) d.push(`${c.horas} h/sem`);
    return d;
  }

  protected nombreCurso(id: number): string {
    return this.datos.cursos().find(c => c.cursoId === id)?.nombre ?? 'Curso';
  }

  protected fecha(iso: string): string {
    return MESES(iso);
  }

  protected readonly nombre = nombreNatural;

  protected inicial(n: string): string {
    return (n.split(',')[1] ?? n).trim().charAt(0).toUpperCase();
  }

  protected primerNombre(n: string): string {
    return (n.split(',')[1] ?? n).trim().split(' ')[0];
  }
}

function fechaCorta(iso: string): string {
  return new Date(iso).toLocaleDateString('es-PE', { day: 'numeric', month: 'short' });
}
