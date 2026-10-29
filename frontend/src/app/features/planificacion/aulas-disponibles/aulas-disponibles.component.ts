import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Anio, Aula, Bloque, ClaseHorario, DIAS_SEMANA, Seccion, TIPOS_AULA, Turno } from '../../../core/models/planificacion.model';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const DIAS = [1, 2, 3, 4, 5];
const hora = (h: string) => h.slice(0, 5);

/**
 * Qué aulas están libres y cuáles ocupadas en cada bloque de la semana.
 * Arriba, un mapa día × bloque con las aulas libres; al elegir un bloque se ve quién ocupa cada aula;
 * al elegir un aula se ve su semana completa.
 */
@Component({
  selector: 'app-aulas-disponibles',
  imports: [PageHeaderComponent, RouterLink],
  template: `
    <app-page-header titulo="Aulas disponibles" subtitulo="Consulta qué aulas están libres en cada bloque antes de armar o mover un horario.">
      <select class="input" aria-label="Año escolar" (change)="cambiarAnio(+$any($event.target).value)">
        @for (a of anios(); track a.id) { <option [value]="a.id" [selected]="a.id === anio()?.id">{{ a.nombre }}</option> }
      </select>
    </app-page-header>

    @if (cargando()) {
      <p class="muted">Cargando aulas y horarios…</p>
    } @else {
      <section class="card"><div class="card-body">
        <div class="toolbar" style="margin-bottom: 12px">
          <div class="chips" role="group" aria-label="Turno">
            <button type="button" class="chip" [class.on]="turno() === 'MANANA'" (click)="turno.set('MANANA'); celda.set(null)">Mañana</button>
            <button type="button" class="chip" [class.on]="turno() === 'TARDE'" (click)="turno.set('TARDE'); celda.set(null)">Tarde</button>
          </div>
          <select class="input" aria-label="Tipo de aula" (change)="tipo.set($any($event.target).value)">
            <option value="" [selected]="tipo() === ''">Todos los tipos</option>
            @for (t of tipos; track t) { <option [value]="t" [selected]="t === tipo()">{{ etiquetaTipo(t) }}</option> }
          </select>
          <label class="muted" style="display: flex; gap: 6px; align-items: center">
            Capacidad mínima
            <input class="input" type="number" min="0" style="width: 80px" [value]="capMin()" (input)="capMin.set(+$any($event.target).value || 0)" />
          </label>
          <span class="muted" style="margin-left: auto">{{ aulasFiltradas().length }} aula(s)</span>
        </div>

        @if (sinAula().length > 0) {
          <p class="aviso aviso-warn">
            {{ sinAula().length }} sección(es) no tienen aula y no se pueden ubicar en un horario:
            {{ nombresSinAula() }}. <a routerLink="/planificacion/panel">Asignarles un aula</a>.
          </p>
        }

        <h3 class="ficha-sub">Aulas libres por bloque</h3>
        <p class="muted" style="margin-top: 0">Cada celda muestra cuántas aulas están libres. Toca una para ver cuáles.</p>
        <div class="table-wrap">
          <table class="table mapa">
            <thead><tr><th>Bloque</th>@for (d of dias; track d) { <th>{{ dia(d) }}</th> }</tr></thead>
            <tbody>
              @for (b of bloques(); track b.id) {
                @if (b.esReceso) {
                  <tr class="receso"><td>{{ hora(b.horaInicio) }}–{{ hora(b.horaFin) }}</td><td [attr.colspan]="dias.length" class="muted">Receso</td></tr>
                } @else {
                  <tr>
                    <td><strong>{{ b.orden }}</strong> <span class="muted" style="margin-left: 6px">{{ hora(b.horaInicio) }}–{{ hora(b.horaFin) }}</span></td>
                    @for (d of dias; track d) {
                      <td>
                        <button type="button" class="celda" [class.sel]="celda()?.bloque === b.id && celda()?.dia === d" [class]="'celda ' + nivel(b.id, d)"
                                [attr.aria-label]="dia(d) + ' bloque ' + b.orden + ': ' + libres(b.id, d).length + ' aulas libres'" (click)="celda.set({ bloque: b.id, dia: d })">
                          {{ libres(b.id, d).length }}<span class="muted"> / {{ aulasFiltradas().length }}</span>
                        </button>
                      </td>
                    }
                  </tr>
                }
              } @empty {
                <tr><td [attr.colspan]="dias.length + 1" class="muted">No hay bloques horarios para este turno. Créalos en «Bloques horarios».</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div></section>

      @if (celda(); as c) {
        <section class="card" style="margin-top: 16px"><div class="card-body">
          <div class="sec-cab">
            <h3 class="sec-titulo">{{ dia(c.dia) }} · bloque {{ bloqueDe(c.bloque)?.orden }} ({{ hora(bloqueDe(c.bloque)!.horaInicio) }}–{{ hora(bloqueDe(c.bloque)!.horaFin) }})</h3>
            <button type="button" class="btn btn-sm" (click)="celda.set(null)">Cerrar</button>
          </div>
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Aula</th><th>Tipo</th><th>Capacidad</th><th>Estado</th><th>Ocupada por</th><th></th></tr></thead>
              <tbody>
                @for (a of aulasFiltradas(); track a.id) {
                  <tr>
                    <td><strong>{{ a.codigo }}</strong> <span class="muted" style="margin-left: 6px">{{ a.nombre }}</span></td>
                    <td>{{ etiquetaTipo(a.tipo) }}</td>
                    <td>{{ a.capacidad }}</td>
                    @if (ocupante(a.id, c.bloque, c.dia); as o) {
                      <td><span class="badge badge-danger">Ocupada</span></td>
                      <td>{{ o.seccionNombre }} · {{ o.cursoNombre }} <span class="muted">({{ o.docenteNombre }})</span></td>
                    } @else {
                      <td><span class="badge badge-ok">Libre</span></td>
                      <td class="muted">—</td>
                    }
                    <td><button type="button" class="enlace" (click)="aula.set(a.id)">Ver su semana</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div></section>
      }

      <section class="card" style="margin-top: 16px"><div class="card-body">
        <div class="sec-cab">
          <h3 class="sec-titulo">Semana de un aula</h3>
          <select class="input" aria-label="Aula" (change)="aula.set(+$any($event.target).value)">
            <option value="0" [selected]="aula() === 0">Elige un aula…</option>
            @for (a of aulasFiltradas(); track a.id) { <option [value]="a.id" [selected]="a.id === aula()">{{ a.codigo }} · {{ a.nombre }}</option> }
          </select>
        </div>
        @if (aula() === 0) {
          <p class="muted">Elige un aula para ver en qué bloques se usa y en cuáles está libre.</p>
        } @else {
          <div class="table-wrap">
            <table class="table mapa">
              <thead><tr><th>Bloque</th>@for (d of dias; track d) { <th>{{ dia(d) }}</th> }</tr></thead>
              <tbody>
                @for (b of bloques(); track b.id) {
                  @if (!b.esReceso) {
                    <tr>
                      <td><strong>{{ b.orden }}</strong> <span class="muted" style="margin-left: 6px">{{ hora(b.horaInicio) }}</span></td>
                      @for (d of dias; track d) {
                        @if (ocupante(aula(), b.id, d); as o) {
                          <td class="ocupada"><strong>{{ o.cursoNombre }}</strong><br /><span class="muted">{{ o.seccionNombre }}</span></td>
                        } @else {
                          <td class="libre">Libre</td>
                        }
                      }
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        }
      </div></section>
    }
  `,
  styles: `
    .mapa td, .mapa th { text-align: center; }
    .mapa td:first-child, .mapa th:first-child { text-align: left; white-space: nowrap; }
    .celda { width: 100%; min-width: 64px; padding: 8px 6px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); font: inherit; font-weight: 600; cursor: pointer; }
    .celda.todas { background: var(--ok-soft); color: var(--ok); }
    .celda.algunas { background: var(--warn-soft); color: var(--warn); }
    .celda.ninguna { background: var(--danger-soft); color: var(--danger); }
    .celda.sel { outline: 2px solid var(--primary); outline-offset: 1px; }
    .receso td { background: #f6f7fb; font-size: .85rem; }
    td.libre { color: var(--ok); background: var(--ok-soft); font-size: .85rem; }
    td.ocupada { background: var(--warn-soft); font-size: .85rem; }
  `
})
export class AulasDisponiblesComponent implements OnInit {
  private readonly api = inject(PlanificacionService);

  protected readonly dias = DIAS;
  protected readonly tipos = TIPOS_AULA;
  protected readonly hora = hora;
  protected readonly dia = (d: number) => DIAS_SEMANA[d];

  protected readonly anios = signal<Anio[]>([]);
  protected readonly anio = signal<Anio | null>(null);
  protected readonly aulas = signal<Aula[]>([]);
  protected readonly todosBloques = signal<Bloque[]>([]);
  protected readonly clases = signal<ClaseHorario[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly cargando = signal(true);

  protected readonly turno = signal<Turno>('MANANA');
  protected readonly tipo = signal('');
  protected readonly capMin = signal(0);
  protected readonly celda = signal<{ bloque: number; dia: number } | null>(null);
  protected readonly aula = signal(0);

  protected readonly bloques = computed(() => this.todosBloques().filter(b => b.turno === this.turno()).sort((a, b) => a.orden - b.orden));
  protected readonly aulasFiltradas = computed(() =>
    this.aulas().filter(a => (!this.tipo() || a.tipo === this.tipo()) && (a.capacidad ?? 0) >= this.capMin())
  );
  /** `aula|bloque|día` → clase que la ocupa. */
  private readonly ocupacion = computed(() => {
    const m = new Map<string, ClaseHorario>();
    for (const c of this.clases()) if (c.aulaId) m.set(`${c.aulaId}|${c.bloqueId}|${c.diaSemana}`, c);
    return m;
  });
  protected readonly sinAula = computed(() => this.secciones().filter(s => s.activo && !s.aulaId));
  protected readonly nombresSinAula = computed(() => this.sinAula().map(s => `${s.gradoNombre} ${s.nombre}`).join(', '));

  protected etiquetaTipo(t: string): string {
    return { AULA: 'Aula', LABORATORIO: 'Laboratorio', COMPUTO: 'Cómputo', AUDITORIO: 'Auditorio', PATIO: 'Patio', OTRO: 'Otro' }[t] ?? t;
  }

  ngOnInit(): void {
    this.api.anios().subscribe(anios => {
      this.anios.set(anios);
      this.anio.set(anios.find(a => a.estado === 'ACTIVO') ?? anios[0] ?? null);
      this.cargar();
    });
  }

  protected cambiarAnio(id: number): void {
    this.anio.set(this.anios().find(a => a.id === id) ?? null);
    this.celda.set(null);
    this.cargar();
  }

  private cargar(): void {
    const a = this.anio();
    this.cargando.set(true);
    forkJoin({
      aulas: this.api.aulas().pipe(catchError(() => of([] as Aula[]))),
      bloques: this.api.bloques().pipe(catchError(() => of([] as Bloque[]))),
      clases: a ? this.api.horariosAnio(a.id).pipe(catchError(() => of([] as ClaseHorario[]))) : of([] as ClaseHorario[]),
      secciones: a ? this.api.secciones(a.id).pipe(catchError(() => of([] as Seccion[]))) : of([] as Seccion[])
    }).subscribe(r => {
      this.aulas.set(r.aulas.filter(x => x.activo));
      this.todosBloques.set(r.bloques);
      this.clases.set(r.clases);
      this.secciones.set(r.secciones);
      this.cargando.set(false);
    });
  }

  protected ocupante(aulaId: number, bloqueId: number, dia: number): ClaseHorario | undefined {
    return this.ocupacion().get(`${aulaId}|${bloqueId}|${dia}`);
  }

  protected libres(bloqueId: number, dia: number): Aula[] {
    return this.aulasFiltradas().filter(a => !this.ocupante(a.id, bloqueId, dia));
  }

  protected nivel(bloqueId: number, dia: number): string {
    const n = this.libres(bloqueId, dia).length;
    return n === 0 ? 'ninguna' : n === this.aulasFiltradas().length ? 'todas' : 'algunas';
  }

  protected bloqueDe(id: number): Bloque | undefined {
    return this.todosBloques().find(b => b.id === id);
  }
}
