import { Component, OnInit, effect, inject, output, signal } from '@angular/core';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { AlumnoResumen } from '../../core/models/alumno.model';
import { Docente, Seccion } from '../../core/models/planificacion.model';
import { AlumnoService } from '../../core/services/alumno.service';
import { MatriculaService } from '../../core/services/matricula.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { nombreCompleto } from '../../core/utils/errores';

/** A quiénes va el aviso, ya resuelto a números de usuario. */
export interface Destino {
  usuarioIds: number[];
  /** Personas elegidas que no tienen una cuenta vinculada: no pueden recibir el aviso. */
  sinUsuario: number;
  resumen: string;
  calculando: boolean;
}

type Modo = 'docentes' | 'familias' | 'personas' | 'usuarios';

const SIN_DESTINO: Destino = { usuarioIds: [], sinUsuario: 0, resumen: 'Aún no elegiste destinatarios', calculando: false };

/**
 * Elegir a quién se envía el aviso: todos o algunos docentes, las familias de una sección, alumnos concretos
 * (a sus apoderados y/o a ellos) o números de usuario sueltos. Siempre se resuelve a `usuarioIds`.
 */
@Component({
  selector: 'app-destinatarios',
  template: `
    <fieldset class="dest">
      <legend>¿A quién va el aviso?</legend>
      <div class="chips" role="group" aria-label="Tipo de destinatario">
        <button type="button" class="chip" [class.on]="modo() === 'docentes'" (click)="cambiarModo('docentes')">Docentes</button>
        <button type="button" class="chip" [class.on]="modo() === 'familias'" (click)="cambiarModo('familias')">Familias de una sección</button>
        <button type="button" class="chip" [class.on]="modo() === 'personas'" (click)="cambiarModo('personas')">Alumnos concretos</button>
        <button type="button" class="chip" [class.on]="modo() === 'usuarios'" (click)="cambiarModo('usuarios')">Por número de usuario</button>
      </div>

      @switch (modo()) {
        @case ('docentes') {
          <label class="check"><input type="checkbox" [checked]="todosDocentes()" (change)="todosDocentes.set(marcado($event)); recalcular()" /> Todos los docentes activos ({{ docentes().length }})</label>
          @if (!todosDocentes()) {
            <input class="input" type="search" placeholder="Buscar un docente…" aria-label="Buscar un docente" [value]="busquedaDocente()" (input)="busquedaDocente.set(texto($event))" />
            <ul class="dest-lista">
              @for (d of docentesVisibles(); track d.id) {
                <li>
                  <label class="check">
                    <input type="checkbox" [checked]="docentesElegidos().has(d.id)" (change)="alternarDocente(d.id)" />
                    {{ nombre(d) }} @if (!d.usuarioId) { <span class="badge badge-warn">sin cuenta</span> }
                  </label>
                </li>
              } @empty { <li class="muted">No hay docentes que coincidan.</li> }
            </ul>
          }
        }
        @case ('familias') {
          <div class="field">
            <label for="d-sec">Sección</label>
            <select id="d-sec" (change)="elegirSeccion(numero($event))">
              <option value="0" [selected]="seccionId() === 0">Elige una sección…</option>
              @for (s of secciones(); track s.id) { <option [value]="s.id" [selected]="s.id === seccionId()">{{ s.gradoNombre }} · {{ s.nombre }}</option> }
            </select>
          </div>
          <div class="checks" role="group" aria-label="Quiénes de la sección">
            <label class="check"><input type="checkbox" [checked]="aApoderados()" (change)="aApoderados.set(marcado($event)); recalcular()" /> Apoderados</label>
            <label class="check"><input type="checkbox" [checked]="aAlumnos()" (change)="aAlumnos.set(marcado($event)); recalcular()" /> Alumnos</label>
          </div>
        }
        @case ('personas') {
          <input class="input" type="search" placeholder="Buscar un alumno por nombre, código o documento…" aria-label="Buscar un alumno" [value]="busquedaAlumno()" (input)="buscarAlumno($event)" />
          @if (resultados().length > 0) {
            <ul class="dest-lista">
              @for (a of resultados(); track a.id) {
                <li><button type="button" class="enlace" (click)="agregarAlumno(a)">+ {{ a.nombreCompleto }}</button> <span class="muted">{{ a.codigo }}</span></li>
              }
            </ul>
          }
          <div class="chips" aria-label="Alumnos elegidos">
            @for (a of alumnosElegidos(); track a.id) {
              <span class="chip on">{{ a.nombreCompleto }} <button type="button" class="chip-x" (click)="quitarAlumno(a.id)" [attr.aria-label]="'Quitar a ' + a.nombreCompleto">×</button></span>
            } @empty { <span class="muted">Aún no elegiste alumnos.</span> }
          </div>
          <div class="checks" role="group" aria-label="A quién del alumno">
            <label class="check"><input type="checkbox" [checked]="aApoderados()" (change)="aApoderados.set(marcado($event)); recalcular()" /> Sus apoderados</label>
            <label class="check"><input type="checkbox" [checked]="aAlumnos()" (change)="aAlumnos.set(marcado($event)); recalcular()" /> El alumno</label>
          </div>
        }
        @case ('usuarios') {
          <div class="field">
            <label for="d-ids">Números de usuario separados por coma</label>
            <input id="d-ids" [value]="idsTexto()" (input)="idsTexto.set(texto($event)); recalcular()" placeholder="4, 5, 6" />
          </div>
        }
      }

      <p [class]="'dest-resumen ' + (destino().usuarioIds.length === 0 ? 'vacio-dest' : '')" role="status">
        @if (destino().calculando) { Calculando destinatarios… } @else { {{ destino().resumen }} }
      </p>
      @if (destino().sinUsuario > 0 && !destino().calculando) {
        <p class="aviso aviso-warn">{{ destino().sinUsuario }} persona(s) no tienen una cuenta vinculada y no recibirán el aviso en la app.</p>
      }
    </fieldset>
  `
})
export class DestinatariosComponent implements OnInit {
  private readonly plan = inject(PlanificacionService);
  private readonly matriculas = inject(MatriculaService);
  private readonly alumnosApi = inject(AlumnoService);

  readonly cambio = output<Destino>();

  protected readonly modo = signal<Modo>('docentes');
  protected readonly destino = signal<Destino>(SIN_DESTINO);

  protected readonly docentes = signal<Docente[]>([]);
  protected readonly todosDocentes = signal(true);
  protected readonly docentesElegidos = signal<Set<number>>(new Set());
  protected readonly busquedaDocente = signal('');

  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly seccionId = signal(0);

  protected readonly aApoderados = signal(true);
  protected readonly aAlumnos = signal(false);

  protected readonly busquedaAlumno = signal('');
  protected readonly resultados = signal<AlumnoResumen[]>([]);
  protected readonly alumnosElegidos = signal<AlumnoResumen[]>([]);

  protected readonly idsTexto = signal('');

  private readonly busqueda$ = new Subject<string>();
  private ronda = 0;

  protected readonly nombre = nombreCompleto;

  constructor() {
    effect(() => this.cambio.emit(this.destino()));
    this.busqueda$
      .pipe(debounceTime(250), distinctUntilChanged(), switchMap(q => (q.trim().length < 2 ? of(null) : this.alumnosApi.listar(q.trim(), '', 0, 6).pipe(catchError(() => of(null))))))
      .subscribe(p => this.resultados.set(p?.contenido ?? []));
  }

  ngOnInit(): void {
    this.plan.docentes('', 0, 200).pipe(catchError(() => of(null))).subscribe(p => {
      this.docentes.set((p?.contenido ?? []).filter(d => d.estado === 'ACTIVO'));
      this.recalcular();
    });
    this.plan.anioActivo().pipe(catchError(() => of(null))).subscribe(a => {
      if (a) this.plan.secciones(a.id).pipe(catchError(() => of([] as Seccion[]))).subscribe(s => this.secciones.set(s));
    });
  }

  protected docentesVisibles(): Docente[] {
    const q = this.busquedaDocente().trim().toLowerCase();
    return this.docentes().filter(d => !q || `${d.nombres} ${d.apellidos} ${d.especialidad ?? ''}`.toLowerCase().includes(q));
  }

  protected cambiarModo(m: Modo): void {
    this.modo.set(m);
    this.aApoderados.set(true);
    this.aAlumnos.set(false);
    this.recalcular();
  }

  protected alternarDocente(id: number): void {
    this.docentesElegidos.update(s => {
      const n = new Set(s);
      if (!n.delete(id)) n.add(id);
      return n;
    });
    this.recalcular();
  }

  protected elegirSeccion(id: number): void {
    this.seccionId.set(id);
    this.recalcular();
  }

  protected buscarAlumno(e: Event): void {
    const q = texto(e);
    this.busquedaAlumno.set(q);
    this.busqueda$.next(q);
  }

  protected agregarAlumno(a: AlumnoResumen): void {
    if (!this.alumnosElegidos().some(x => x.id === a.id)) this.alumnosElegidos.update(l => [...l, a]);
    this.busquedaAlumno.set('');
    this.resultados.set([]);
    this.recalcular();
  }

  protected quitarAlumno(id: number): void {
    this.alumnosElegidos.update(l => l.filter(a => a.id !== id));
    this.recalcular();
  }

  /** Vuelve a calcular a quiénes llega el aviso según lo elegido; las respuestas viejas se descartan. */
  protected recalcular(): void {
    const ronda = ++this.ronda;
    const poner = (d: Destino): void => {
      if (ronda === this.ronda) this.destino.set(d);
    };
    switch (this.modo()) {
      case 'docentes': {
        const lista = this.todosDocentes() ? this.docentes() : this.docentes().filter(d => this.docentesElegidos().has(d.id));
        const con = lista.filter(d => d.usuarioId);
        poner(this.armar(con.map(d => d.usuarioId!), lista.length - con.length, lista.length === 0 ? 'Aún no elegiste destinatarios' : `Docentes elegidos: ${lista.length}`));
        break;
      }
      case 'usuarios': {
        const ids = this.idsTexto().split(/[\s,;]+/).filter(Boolean).map(Number);
        const ok = ids.length > 0 && ids.every(n => Number.isInteger(n) && n > 0);
        poner(ok ? this.armar(ids, 0, 'Usuarios indicados') : { ...SIN_DESTINO, resumen: ids.length ? 'Los números de usuario no son válidos' : SIN_DESTINO.resumen });
        break;
      }
      case 'familias':
        if (this.seccionId() === 0) {
          poner(SIN_DESTINO);
        } else {
          poner({ ...SIN_DESTINO, calculando: true });
          this.matriculas.alumnosDeSeccion(this.seccionId()).pipe(catchError(() => of([]))).subscribe(l =>
            this.resolverAlumnos(l.filter(a => a.estado === 'MATRICULADO').map(a => a.alumnoId), 'Familias de la sección', ronda, poner));
        }
        break;
      case 'personas':
        if (this.alumnosElegidos().length === 0) poner(SIN_DESTINO);
        else {
          poner({ ...SIN_DESTINO, calculando: true });
          this.resolverAlumnos(this.alumnosElegidos().map(a => a.id), 'Alumnos elegidos', ronda, poner);
        }
        break;
    }
  }

  private resolverAlumnos(ids: number[], etiqueta: string, ronda: number, poner: (d: Destino) => void): void {
    if (ids.length === 0) {
      poner({ ...SIN_DESTINO, resumen: 'La sección no tiene alumnos matriculados' });
      return;
    }
    if (!this.aApoderados() && !this.aAlumnos()) {
      poner({ ...SIN_DESTINO, resumen: 'Marca si va a los apoderados, a los alumnos o a ambos' });
      return;
    }
    forkJoin(ids.map(id => forkJoin({
      alumno: this.alumnosApi.obtener(id).pipe(catchError(() => of(null))),
      vinculos: this.aApoderados() ? this.alumnosApi.vinculos(id).pipe(catchError(() => of([]))) : of([])
    }))).subscribe(filas => {
      if (ronda !== this.ronda) return;
      const usuarios = new Set<number>();
      let sin = 0;
      const apoderadosVistos = new Set<number>();
      for (const f of filas) {
        if (this.aAlumnos() && f.alumno) {
          if (f.alumno.usuarioId) usuarios.add(f.alumno.usuarioId);
          else sin++;
        }
        for (const v of f.vinculos) {
          if (apoderadosVistos.has(v.apoderado.id)) continue; // hermanos: un apoderado recibe un solo aviso
          apoderadosVistos.add(v.apoderado.id);
          if (v.apoderado.usuarioId) usuarios.add(v.apoderado.usuarioId);
          else sin++;
        }
      }
      poner(this.armar([...usuarios], sin, `${etiqueta}: ${ids.length} alumno(s)`));
    });
  }

  private armar(usuarioIds: number[], sinUsuario: number, base: string): Destino {
    const ids = [...new Set(usuarioIds)];
    return {
      usuarioIds: ids,
      sinUsuario,
      calculando: false,
      resumen: ids.length === 0 ? `${base}. Ninguno tiene cuenta para recibir el aviso.` : `${base}. Se enviará a ${ids.length} usuario(s).`
    };
  }

  protected readonly texto = texto;
  protected marcado(e: Event): boolean {
    return (e.target as HTMLInputElement).checked;
  }
  protected numero(e: Event): number {
    return Number((e.target as HTMLSelectElement).value);
  }
}

function texto(e: Event): string {
  return (e.target as HTMLInputElement).value;
}
