import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Asignacion, Curso, Docente, Grado, Seccion } from '../../core/models/planificacion.model';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { mensajeError, nombreCompleto } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { AsignarDocenteComponent } from './asignar-docente.component';

type Vista = 'cursos' | 'docentes';

const texto = (e: Event): string => (e.target as HTMLInputElement | HTMLSelectElement).value;

/** Qué cursos hay, quién los dicta y en qué secciones; con altas rápidas de curso y de asignación de docente. */
@Component({
  selector: 'app-cursos-docentes',
  imports: [RouterLink, AsignarDocenteComponent, ModalComponent],
  template: `
    <section class="card" aria-labelledby="t-cd">
      <div class="card-body">
        <div class="sec-cab">
          <h2 id="t-cd" class="sec-titulo">Cursos y docentes</h2>
          <div class="toolbar">
            @if (esAdmin()) { <button type="button" class="btn btn-sm" (click)="abrirCurso()">Nuevo curso</button> }
            <button type="button" class="btn btn-sm btn-primary" (click)="asignar.set({ cursoId: 0, docenteId: 0 })">Asignar docente</button>
          </div>
        </div>

        <div class="mini-stats">
          <div class="mini"><span class="mini-v">{{ cursos().length }}</span><span class="muted">cursos</span></div>
          <div class="mini"><span class="mini-v">{{ docentesActivos().length }}</span><span class="muted">docentes activos</span></div>
          <div class="mini"><span class="mini-v">{{ asignaciones().length }}</span><span class="muted">asignaciones</span></div>
          <div class="mini" [class.alerta]="docentesSinCarga().length > 0"><span class="mini-v">{{ docentesSinCarga().length }}</span><span class="muted">docentes sin cursos</span></div>
        </div>

        <div class="toolbar" style="margin: 14px 0 10px">
          <div class="chips" role="group" aria-label="Ver por">
            <button type="button" class="chip" [class.on]="vista() === 'cursos'" (click)="vista.set('cursos')">Por curso</button>
            <button type="button" class="chip" [class.on]="vista() === 'docentes'" (click)="vista.set('docentes')">Por docente</button>
          </div>
          <input class="input grow" type="search" [placeholder]="vista() === 'cursos' ? 'Buscar un curso…' : 'Buscar un docente…'" aria-label="Buscar"
                 [value]="busqueda()" (input)="busqueda.set(texto($event))" />
        </div>

        @if (cargando()) {
          <p class="muted">Cargando cursos y asignaciones…</p>
        } @else if (vista() === 'cursos') {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Curso</th><th>Docentes que lo dictan</th><th>Secciones</th><th></th></tr></thead>
              <tbody>
                @for (c of cursosVisibles(); track c.id) {
                  <tr>
                    <td><strong>{{ c.nombre }}</strong><br /><span class="muted">{{ c.codigo }}{{ c.area ? ' · ' + c.area : '' }}</span></td>
                    <td>
                      @for (d of docentesDe(c.id); track d.nombre) { <span class="badge badge-gray" style="margin: 0 4px 4px 0">{{ d.nombre }} · {{ d.secciones }}</span> }
                      @empty { <span class="badge badge-warn">Sin docente</span> }
                    </td>
                    <td>{{ seccionesDe(c.id) }}</td>
                    <td class="acciones"><button type="button" class="btn btn-sm" (click)="asignar.set({ cursoId: c.id, docenteId: 0 })">Asignar</button></td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="muted">No hay cursos que coincidan.</td></tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Docente</th><th>Cursos que dicta</th><th>Carga</th><th></th></tr></thead>
              <tbody>
                @for (d of docentesVisibles(); track d.id) {
                  <tr>
                    <td><strong>{{ nombre(d) }}</strong><br /><span class="muted">{{ d.especialidad ?? 'Sin especialidad' }}</span></td>
                    <td>
                      @for (a of asignacionesDe(d.id); track a.id) { <span class="badge badge-gray" style="margin: 0 4px 4px 0">{{ a.cursoNombre }} · {{ a.seccionNombre }}</span> }
                      @empty { <span class="badge badge-warn">Sin cursos</span> }
                    </td>
                    <td>{{ asignacionesDe(d.id).length }}</td>
                    <td class="acciones"><button type="button" class="btn btn-sm" (click)="asignar.set({ cursoId: 0, docenteId: d.id })">Asignar curso</button></td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="muted">No hay docentes que coincidan.</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
        <p class="muted" style="margin-top: 10px">
          Un curso solo se puede asignar si está en la malla del grado. <a routerLink="/planificacion/malla">Abrir la malla curricular</a>
        </p>
      </div>
    </section>

    @if (asignar(); as a) {
      <app-asignar-docente [secciones]="secciones()" [docentes]="docentesActivos()" [cursoId]="a.cursoId" [docenteId]="a.docenteId"
                           (cerrar)="asignar.set(null)" (guardado)="alAsignar()" />
    }

    @if (cursoAbierto()) {
      <app-modal titulo="Nuevo curso" [ancho]="520" (cerrar)="cursoAbierto.set(false)">
        <div class="form-grid">
          <div class="field">
            <label for="nc-cod">Código</label>
            <input id="nc-cod" maxlength="20" [value]="nuevo().codigo" (input)="cambiar('codigo', texto($event))" placeholder="Ej. MAT" />
          </div>
          <div class="field">
            <label for="nc-area">Área (opcional)</label>
            <input id="nc-area" maxlength="60" [value]="nuevo().area" (input)="cambiar('area', texto($event))" placeholder="Ej. Ciencias" />
          </div>
          <div class="field full">
            <label for="nc-nom">Nombre del curso</label>
            <input id="nc-nom" maxlength="100" [value]="nuevo().nombre" (input)="cambiar('nombre', texto($event))" placeholder="Ej. Matemática" />
          </div>
          <div class="field">
            <label for="nc-gra">Agregar a la malla de (opcional)</label>
            <select id="nc-gra" (change)="cambiar('gradoId', texto($event))">
              <option value="0" [selected]="nuevo().gradoId === '0'">Solo crear el curso</option>
              @for (g of grados(); track g.id) { <option [value]="g.id" [selected]="nuevo().gradoId === '' + g.id">{{ g.nombre }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="nc-hor">Horas por semana</label>
            <input id="nc-hor" type="number" min="1" max="40" [disabled]="nuevo().gradoId === '0'" [value]="nuevo().horas" (input)="cambiar('horas', texto($event))" />
          </div>
        </div>
        <p class="muted" style="margin-top: 10px">Sin malla el curso existe, pero aún no se puede asignar a una sección. Puedes sumarlo a más grados en «Malla curricular».</p>
        @if (errorCurso()) { <div class="aviso aviso-danger" style="margin-top: 12px" role="alert">{{ errorCurso() }}</div> }
        <ng-container footer>
          <button type="button" class="btn" (click)="cursoAbierto.set(false)">Cancelar</button>
          <button type="button" class="btn btn-primary" [disabled]="!cursoValido() || guardandoCurso()" (click)="crearCurso()">Crear curso</button>
        </ng-container>
      </app-modal>
    }
  `
})
export class CursosDocentesComponent implements OnInit {
  private readonly api = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);

  readonly secciones = input.required<Seccion[]>();
  readonly docentes = input.required<Docente[]>();
  readonly esAdmin = input(false);
  /** Avisa al inicio que cambiaron las asignaciones (sus tarjetas y alertas se actualizan). */
  readonly cambio = output<void>();

  protected readonly cursos = signal<Curso[]>([]);
  protected readonly asignaciones = signal<Asignacion[]>([]);
  protected readonly grados = signal<Grado[]>([]);
  protected readonly cargando = signal(true);
  protected readonly vista = signal<Vista>('cursos');
  protected readonly busqueda = signal('');
  protected readonly asignar = signal<{ cursoId: number; docenteId: number } | null>(null);

  protected readonly cursoAbierto = signal(false);
  protected readonly guardandoCurso = signal(false);
  protected readonly errorCurso = signal<string | null>(null);
  protected readonly nuevo = signal({ codigo: '', nombre: '', area: '', gradoId: '0', horas: '2' });

  protected readonly texto = texto;
  protected readonly nombre = nombreCompleto;

  protected readonly docentesActivos = computed(() => this.docentes().filter(d => d.estado === 'ACTIVO'));
  protected readonly docentesSinCarga = computed(() => {
    const con = new Set(this.asignaciones().map(a => a.docenteId));
    return this.docentesActivos().filter(d => !con.has(d.id));
  });

  protected readonly cursosVisibles = computed(() => {
    const q = this.busqueda().trim().toLowerCase();
    return this.cursos().filter(c => c.activo && (!q || `${c.nombre} ${c.codigo} ${c.area ?? ''}`.toLowerCase().includes(q)));
  });

  protected readonly docentesVisibles = computed(() => {
    const q = this.busqueda().trim().toLowerCase();
    return this.docentesActivos().filter(d => !q || `${d.nombres} ${d.apellidos} ${d.especialidad ?? ''}`.toLowerCase().includes(q));
  });

  protected readonly cursoValido = computed(() => {
    const n = this.nuevo();
    const horas = Number(n.horas);
    return n.codigo.trim().length > 0 && n.nombre.trim().length > 0 && (n.gradoId === '0' || (Number.isInteger(horas) && horas >= 1 && horas <= 40));
  });

  ngOnInit(): void {
    this.cargar();
    this.api.grados().subscribe({ next: g => this.grados.set(g), error: () => this.grados.set([]) });
  }

  private cargar(): void {
    // Una sola consulta con todas las asignaciones del año (antes eran una por docente y saturaban el límite de peticiones).
    const anioId = this.secciones()[0]?.anioId;
    forkJoin({ cursos: this.api.cursos(), asignaciones: anioId ? this.api.asignaciones(undefined, undefined, anioId) : of([] as Asignacion[]) }).subscribe({
      next: r => {
        this.cursos.set(r.cursos);
        const ids = new Set(this.secciones().map(s => s.id));
        this.asignaciones.set(r.asignaciones.filter(a => ids.has(a.seccionId)));
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los cursos.'));
      }
    });
  }

  protected docentesDe(cursoId: number): { nombre: string; secciones: string }[] {
    const mapa = new Map<string, number>();
    for (const a of this.asignaciones().filter(x => x.cursoId === cursoId)) mapa.set(a.docenteNombre, (mapa.get(a.docenteNombre) ?? 0) + 1);
    return [...mapa].map(([nombre, n]) => ({ nombre, secciones: n === 1 ? '1 sección' : `${n} secciones` }));
  }

  protected seccionesDe(cursoId: number): number {
    return new Set(this.asignaciones().filter(a => a.cursoId === cursoId).map(a => a.seccionId)).size;
  }

  protected asignacionesDe(docenteId: number): Asignacion[] {
    return this.asignaciones().filter(a => a.docenteId === docenteId);
  }

  protected alAsignar(): void {
    this.asignar.set(null);
    this.cargar();
    this.cambio.emit();
  }

  protected abrirCurso(): void {
    this.nuevo.set({ codigo: '', nombre: '', area: '', gradoId: '0', horas: '2' });
    this.errorCurso.set(null);
    this.cursoAbierto.set(true);
  }

  protected cambiar(campo: 'codigo' | 'nombre' | 'area' | 'gradoId' | 'horas', valor: string): void {
    this.nuevo.update(n => ({ ...n, [campo]: valor }));
  }

  protected crearCurso(): void {
    if (!this.cursoValido()) return;
    const n = this.nuevo();
    this.guardandoCurso.set(true);
    this.errorCurso.set(null);
    this.api.crearCurso({ codigo: n.codigo.trim().toUpperCase(), nombre: n.nombre.trim(), area: n.area.trim() || undefined, activo: true }).subscribe({
      next: curso => {
        const terminar = (mensaje: string): void => {
          this.guardandoCurso.set(false);
          this.cursoAbierto.set(false);
          this.avisos.success(mensaje);
          this.cargar();
        };
        if (n.gradoId === '0') {
          terminar(`Curso «${curso.nombre}» creado. Agrégalo a la malla de un grado para poder asignarlo.`);
          return;
        }
        this.api.agregarCursoMalla(Number(n.gradoId), curso.id, Number(n.horas)).subscribe({
          next: () => terminar(`Curso «${curso.nombre}» creado y agregado a la malla.`),
          error: e => {
            terminar(`Curso «${curso.nombre}» creado, pero no se pudo agregar a la malla: ${mensajeError(e)}`);
          }
        });
      },
      error: e => {
        this.guardandoCurso.set(false);
        this.errorCurso.set(mensajeError(e));
      }
    });
  }
}
