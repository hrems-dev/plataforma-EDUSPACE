import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { MatriculaService } from '../../core/services/matricula.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

type Tipo = 'feriado' | 'periodo' | 'campania';
interface Evento { fecha: string; titulo: string; tipo: Tipo }

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const iso = (d: Date): string => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Calendario escolar del año activo: feriados, inicio y fin de cada periodo y, para el personal, las campañas de matrícula. */
@Component({
  selector: 'app-calendario',
  imports: [DatePipe, PageHeaderComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Calendario escolar" subtitulo="Feriados, periodos y fechas importantes del año.">
        <button type="button" class="btn btn-sm" (click)="mover(-1)" aria-label="Mes anterior">‹</button>
        <strong class="mes-nombre" aria-live="polite">{{ nombreMes() }}</strong>
        <button type="button" class="btn btn-sm" (click)="mover(1)" aria-label="Mes siguiente">›</button>
        <button type="button" class="btn btn-sm" (click)="hoy()">Hoy</button>
      </app-page-header>

      <div class="leyenda" aria-label="Leyenda">
        <span><i class="p fer"></i> Feriado</span><span><i class="p per"></i> Periodo</span>
        @if (staff) { <span><i class="p cam"></i> Campaña de matrícula</span> }
      </div>

      <div class="dos-col" style="grid-template-columns: 2fr 1fr">
        <div class="card"><div class="card-body">
          <div class="cal" role="grid" [attr.aria-label]="nombreMes()">
            @for (d of cabecera; track $index) { <div class="cab" role="columnheader">{{ d }}</div> }
            @for (c of celdas(); track c.fecha) {
              <button type="button" class="dia" role="gridcell" [class.fuera]="!c.delMes" [class.hoy]="c.fecha === hoyIso" [class.sel]="c.fecha === seleccion()" (click)="seleccion.set(c.fecha)"
                [attr.aria-label]="c.fecha + (c.eventos.length ? ', ' + c.eventos.length + ' evento(s)' : '')">
                <span class="n">{{ c.numero }}</span>
                @for (e of c.eventos.slice(0, 2); track e.titulo) { <span [class]="'ev ' + e.tipo" [title]="e.titulo">{{ e.titulo }}</span> }
                @if (c.eventos.length > 2) { <span class="mas">+{{ c.eventos.length - 2 }} más</span> }
              </button>
            }
          </div>
        </div></div>

        <aside class="card"><div class="card-body">
          <h2 class="sec-titulo">{{ seleccion() | date: 'EEEE d MMMM' }}</h2>
          @for (e of delDia(); track e.titulo) { <div [class]="'detalle ' + e.tipo"><strong>{{ e.titulo }}</strong><span class="muted">{{ etiqueta(e.tipo) }}</span></div> }
          @empty { <p class="muted">Sin eventos este día.</p> }
          <h2 class="sec-titulo" style="margin-top: 22px">Próximos</h2>
          @for (e of proximos(); track e.fecha + e.titulo) {
            <div class="prox"><span class="muted">{{ e.fecha | date: 'd MMM' }}</span> {{ e.titulo }}</div>
          } @empty { <p class="muted">No hay más fechas registradas.</p> }
        </div></aside>
      </div>
    </div>
  `,
  styles: `
    .mes-nombre { min-width: 150px; text-align: center; text-transform: capitalize; }
    .leyenda { display: flex; gap: 18px; flex-wrap: wrap; font-size: .85rem; color: var(--ink-2); margin: 6px 0 12px; }
    .p { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 4px; }
    .fer { background: var(--danger); } .per { background: var(--primary); } .cam { background: #0e9f6e; }
    .cal { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 4px; }
    .cab { text-align: center; font-size: .75rem; font-weight: 700; color: var(--ink-2); padding: 4px 0; text-transform: uppercase; }
    .dia { min-height: 82px; border: 1px solid var(--line); border-radius: 8px; background: #fff; padding: 4px; text-align: left; display: flex; flex-direction: column; gap: 2px; cursor: pointer; font: inherit; overflow: hidden; }
    .dia:hover { border-color: var(--primary); }
    .dia.fuera { background: var(--bg); color: #98a2b8; }
    .dia.hoy .n { background: var(--primary); color: #fff; border-radius: 99px; }
    .dia.sel { outline: 2px solid var(--primary); outline-offset: -1px; }
    .n { font-size: .8rem; font-weight: 700; width: 22px; height: 22px; display: grid; place-items: center; }
    .ev { font-size: .68rem; border-radius: 4px; padding: 1px 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .ev.feriado { background: var(--danger-soft); color: var(--danger); } .ev.periodo { background: var(--primary-soft); color: var(--primary-hover); } .ev.campania { background: #d9f5e8; color: #0b5d41; }
    .mas { font-size: .66rem; color: var(--ink-2); }
    .detalle { border-left: 4px solid var(--primary); padding: 6px 10px; margin-bottom: 8px; background: var(--bg); border-radius: 6px; display: flex; flex-direction: column; }
    .detalle.feriado { border-color: var(--danger); } .detalle.campania { border-color: #0e9f6e; }
    .prox { padding: 5px 0; border-bottom: 1px solid var(--line); font-size: .9rem; }
    @media (max-width: 900px) { .dos-col { grid-template-columns: 1fr !important; } .dia { min-height: 56px; } .ev { display: none; } }
  `
})
export class CalendarioComponent implements OnInit {
  private readonly plan = inject(PlanificacionService);
  private readonly matriculas = inject(MatriculaService);
  protected readonly staff = inject(AuthService).tieneAlgunRol(['ADMIN', 'SECRETARIA']);

  protected readonly cabecera = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
  protected readonly hoyIso = iso(new Date());
  protected readonly cursor = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  protected readonly seleccion = signal(this.hoyIso);
  private readonly eventos = signal<Evento[]>([]);

  protected readonly nombreMes = computed(() => `${MESES[this.cursor().getMonth()]} ${this.cursor().getFullYear()}`);

  protected readonly celdas = computed(() => {
    const c = this.cursor();
    const primero = new Date(c.getFullYear(), c.getMonth(), 1);
    const desfase = (primero.getDay() + 6) % 7; // la semana empieza el lunes
    return Array.from({ length: 42 }, (_, i) => {
      const d = new Date(c.getFullYear(), c.getMonth(), 1 - desfase + i);
      const fecha = iso(d);
      return { fecha, numero: d.getDate(), delMes: d.getMonth() === c.getMonth(), eventos: this.eventos().filter(e => e.fecha === fecha) };
    });
  });

  protected readonly delDia = computed(() => this.eventos().filter(e => e.fecha === this.seleccion()));
  protected readonly proximos = computed(() => this.eventos().filter(e => e.fecha >= this.hoyIso).sort((a, b) => a.fecha.localeCompare(b.fecha)).slice(0, 6));

  ngOnInit(): void {
    this.plan.anioActivo().pipe(catchError(() => of(null))).subscribe(anio => {
      if (!anio) return;
      forkJoin({
        feriados: this.plan.feriados(anio.id).pipe(catchError(() => of([]))),
        periodos: this.plan.periodos(anio.id).pipe(catchError(() => of([]))),
        campanias: this.staff ? this.matriculas.campanias(anio.id).pipe(catchError(() => of([]))) : of([])
      }).subscribe(({ feriados, periodos, campanias }) => {
        this.eventos.set([
          ...feriados.map(f => ({ fecha: f.fecha, titulo: f.descripcion, tipo: 'feriado' as const })),
          ...periodos.flatMap(p => [
            { fecha: p.fechaInicio, titulo: `Inicia ${p.nombre}`, tipo: 'periodo' as const },
            { fecha: p.fechaFin, titulo: `Termina ${p.nombre}`, tipo: 'periodo' as const }
          ]),
          ...campanias.flatMap(c => [
            { fecha: c.fechaInicio, titulo: `Abre ${c.nombre}`, tipo: 'campania' as const },
            { fecha: c.fechaFin, titulo: `Cierra ${c.nombre}`, tipo: 'campania' as const }
          ])
        ]);
      });
    });
  }

  protected mover(delta: number): void {
    const c = this.cursor();
    this.cursor.set(new Date(c.getFullYear(), c.getMonth() + delta, 1));
  }

  protected hoy(): void {
    const h = new Date();
    this.cursor.set(new Date(h.getFullYear(), h.getMonth(), 1));
    this.seleccion.set(this.hoyIso);
  }

  protected etiqueta(t: Tipo): string {
    return t === 'feriado' ? 'Feriado' : t === 'periodo' ? 'Periodo académico' : 'Campaña de matrícula';
  }
}
