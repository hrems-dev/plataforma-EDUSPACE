import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ExamenDisponible, IntentoResultado } from '../../../core/models/examen.model';
import { DIAS_SEMANA } from '../../../core/models/planificacion.model';
import { ExamenService } from '../../../core/services/examen.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { nombreNatural } from '../../../shared/aula/nombres';
import { CursoCabeceraComponent } from '../../../shared/aula/curso-cabecera.component';
import { PestanaCurso, PestanasCursoComponent } from '../../../shared/aula/pestanas-curso.component';
import { MaterialesCursoComponent } from '../../../shared/aula/materiales-curso.component';
import { FamiliaTareasComponent } from '../tareas/familia-tareas.component';
import { FamiliaDatosService } from './familia-datos.service';

/** Un curso del alumno: sus tareas (con entrega), sus notas por periodo y los exámenes, en una sola página. */
@Component({
  selector: 'app-familia-curso',
  imports: [DatePipe, RouterLink, CursoCabeceraComponent, PestanasCursoComponent, FamiliaTareasComponent, MaterialesCursoComponent],
  template: `
    @if (curso(); as c) {
      <app-curso-cabecera [cursoId]="c.cursoId" [nombre]="c.nombre" volverA="/familia/inicio"
        [subtitulo]="(c.docente ?? 'Docente por asignar') + (ctx.seleccionado() ? ' · ' + nombre(ctx.seleccionado()!.nombreCompleto) : '')">
        @if (c.promedio !== null) { <span class="nota-chip">Promedio <strong>{{ c.promedio }}</strong>{{ c.literal ? ' · ' + c.literal : '' }}</span> }
      </app-curso-cabecera>

      <app-pestanas-curso [pestanas]="pestanas()" [(activa)]="pestana" />

      @switch (pestana()) {
        @case ('tareas') { <app-familia-tareas [cursoId]="c.cursoId" [embebido]="true" /> }
        @case ('materiales') {
          @if (ctx.matriculaActual(); as m) { <app-materiales-curso [seccionId]="m.seccionId" [cursoId]="c.cursoId" /> }
        }
        @case ('notas') {
          <section class="card"><div class="card-body">
            @if (periodos().length === 0) {
              <p class="muted">Todavía no hay notas de este curso. Aparecen cuando el docente las registra.</p>
            } @else {
              <div class="notas">
                @for (p of periodos(); track p.periodoId) {
                  <div class="nota"><span class="muted">Periodo {{ p.periodoId }}</span><strong>{{ p.promedio }}</strong><span [class]="'badge ' + (p.estado === 'CERRADO' ? 'badge-ok' : 'badge-warn')">{{ p.literal }} · {{ p.estado === 'CERRADO' ? 'cerrado' : 'en curso' }}</span></div>
                }
                @if (c.promedio !== null) { <div class="nota final"><span class="muted">Promedio</span><strong>{{ c.promedio }}</strong><span class="badge badge-violet">{{ c.literal ?? '' }}</span></div> }
              </div>
            }
            <a class="btn btn-sm" routerLink="/familia/notas" style="margin-top: 14px">Ver todas las notas y boletines</a>
          </div></section>
        }
        @case ('examenes') {
          <section class="card"><div class="card-body">
            @for (e of disponibles(); track e.examenId) {
              <div class="fila">
                <div><strong>{{ e.titulo }}</strong><div class="muted">Del {{ e.fechaInicio | date: 'dd/MM HH:mm' }} al {{ e.fechaFin | date: 'dd/MM HH:mm' }} · {{ e.duracionMin }} min</div></div>
                @if (e.puedeRendir) { <a class="btn btn-sm btn-primary" [routerLink]="['/familia/examenes', e.examenId, 'rendir']">Rendir</a> } @else { <span class="badge badge-gray">{{ e.intentosRealizados }}/{{ e.intentosMax }} intentos</span> }
              </div>
            } @empty {
              <p class="muted">No hay exámenes de este curso disponibles ahora.</p>
            }
            <a class="btn btn-sm" routerLink="/familia/examenes" style="margin-top: 14px">Ver todos mis resultados</a>
          </div></section>
        }
      }
    } @else if (datos.cargando()) {
      <p class="muted">Cargando el curso…</p>
    } @else {
      <div class="aviso aviso-warn">No encontramos este curso. <a routerLink="/familia/inicio">Volver a Mis cursos</a></div>
    }
  `,
  styles: `
    .nota-chip { background: rgb(255 255 255 / .18); border: 1px solid rgb(255 255 255 / .35); border-radius: 999px; padding: 6px 14px; color: #fff; }
    .notas { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; }
    .nota { display: flex; flex-direction: column; gap: 4px; padding: 14px; border: 1px solid var(--line); border-radius: 14px; }
    .nota strong { font-size: 1.8rem; }
    .nota.final { background: var(--primary-soft); border-color: transparent; }
    .fila { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid var(--line); }
    .fila:first-child { border-top: 0; }
  `
})
export class FamiliaCursoComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  protected readonly datos = inject(FamiliaDatosService);
  private readonly examenesApi = inject(ExamenService);

  /** Parámetros de la ruta (`:cursoId`) y consulta (`?ver=tareas|notas|examenes`). */
  protected readonly nombre = nombreNatural;
  readonly cursoId = input.required<string>();
  readonly ver = input<string | undefined>(undefined);

  protected readonly pestana = signal('tareas');
  protected readonly disponibles = signal<ExamenDisponible[]>([]);
  protected readonly resultados = signal<IntentoResultado[]>([]);
  protected readonly dias = DIAS_SEMANA;

  protected readonly curso = computed(() => this.datos.cursos().find(c => c.cursoId === Number(this.cursoId())) ?? null);
  protected readonly periodos = computed(() => {
    const l = this.datos.libretas().find(x => x.alumnoId === this.ctx.seleccionado()?.alumnoId);
    const n = l?.cursos.find(c => c.cursoId === Number(this.cursoId()));
    return Object.values(n?.notasPorPeriodo ?? {});
  });
  protected readonly pestanas = computed<PestanaCurso[]>(() => [
    { id: 'tareas', etiqueta: 'Tareas', cuenta: this.curso()?.pendientes },
    { id: 'materiales', etiqueta: 'Materiales' },
    { id: 'notas', etiqueta: 'Notas' },
    { id: 'examenes', etiqueta: 'Exámenes', cuenta: this.disponibles().filter(e => e.puedeRendir).length }
  ]);

  constructor() {
    effect(() => {
      const v = this.ver();
      if (v === 'tareas' || v === 'materiales' || v === 'notas' || v === 'examenes') untracked(() => this.pestana.set(v));
    });
  }

  ngOnInit(): void {
    this.ctx.cargar().subscribe();
    if (!this.ctx.esApoderado()) {
      this.examenesApi.disponibles().subscribe({
        next: d => this.disponibles.set(d.filter(e => e.cursoId === Number(this.cursoId()))),
        error: () => this.disponibles.set([])
      });
    }
  }
}
