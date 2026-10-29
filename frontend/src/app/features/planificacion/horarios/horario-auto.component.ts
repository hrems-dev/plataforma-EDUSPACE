import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Anio, Curso, Grado, PreparacionSeccion, ResultadoGeneracion } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const valor = (e: Event): string => (e.target as HTMLInputElement | HTMLSelectElement).value;
const marcado = (e: Event): boolean => (e.target as HTMLInputElement).checked;

/**
 * Armado automático de horarios en tres pasos: (1) cursos por grado, (2) qué grados están listos, (3) generar sin cruces.
 * El algoritmo vive en planificacion-ms; aquí solo se elige qué armar y se revisa el resultado.
 */
@Component({
  selector: 'app-horario-auto',
  imports: [RouterLink, PageHeaderComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Armado automático de horarios" subtitulo="Usa la malla de cada grado y los docentes asignados a cada curso para armar los horarios sin cruces de docente, aula ni sección.">
        <a class="btn" routerLink="/planificacion/horarios">Ver horarios por sección</a>
      </app-page-header>

      <div class="toolbar">
        <select class="input" aria-label="Año académico" (change)="cambiarAnio(numero($event))">
          @for (a of anios(); track a.id) { <option [value]="a.id" [selected]="a.id === anioId()">Año {{ a.nombre }}</option> }
        </select>
        <a class="btn btn-sm" routerLink="/planificacion/cursos-docentes">Cursos y docentes</a>
        <a class="btn btn-sm" routerLink="/planificacion/panel">Aulas y secciones</a>
        <a class="btn btn-sm" routerLink="/planificacion/bloques">Bloques horarios</a>
        <a class="btn btn-sm" routerLink="/planificacion/aulas-disponibles">Aulas disponibles</a>
      </div>

      <!-- 1. Cursos por grado -->
      <section class="card" aria-labelledby="t-malla">
        <div class="card-body">
          <div class="sec-cab">
            <h2 id="t-malla" class="sec-titulo">1. Cursos de cada grado</h2>
            <span class="badge badge-gray">{{ gradosElegidos().size }} grado(s) · {{ cursosElegidos().size }} curso(s)</span>
          </div>
          @if (!esAdmin()) {
            <p class="muted">Solo el administrador define los cursos de cada grado. Aquí abajo puedes ver cómo está cada uno.</p>
          }
          <h3 class="ficha-sub">Grados</h3>
          <div class="chips" role="group" aria-label="Elegir grados por nivel">
            @for (n of niveles(); track n.id) {
              <button type="button" class="chip" [class.on]="nivelCompleto(n.id)" [disabled]="!esAdmin()" (click)="alternarNivel(n.id)">Todos los de {{ n.nombre }}</button>
            }
          </div>
          <div class="grados-grid">
            @for (g of grados(); track g.id) {
              <label class="grado-op" [class.on]="gradosElegidos().has(g.id)">
                <input type="checkbox" [checked]="gradosElegidos().has(g.id)" [disabled]="!esAdmin()" (change)="alternarGrado(g.id)" />
                <span><strong>{{ g.nombre }}</strong><br /><span class="muted">{{ resumenGrado(g.id) }}</span></span>
              </label>
            }
          </div>

          @if (esAdmin()) {
            <h3 class="ficha-sub">Cursos y horas por semana</h3>
            <div class="cursos-grid">
              @for (c of cursos(); track c.id) {
                <div class="curso-op" [class.on]="cursosElegidos().has(c.id)">
                  <label class="check">
                    <input type="checkbox" [checked]="cursosElegidos().has(c.id)" (change)="alternarCurso(c.id)" />
                    {{ c.nombre }}
                  </label>
                  @if (cursosElegidos().has(c.id)) {
                    <span class="horas"><input type="number" min="1" max="40" class="input" [value]="cursosElegidos().get(c.id)" (input)="cambiarHoras(c.id, numero($event))" [attr.aria-label]="'Horas por semana de ' + c.nombre" /> h</span>
                  }
                </div>
              }
            </div>
            <div class="toolbar" style="margin-top: 12px">
              <label class="check"><input type="checkbox" [checked]="sobrescribir()" (change)="sobrescribir.set(marcado($event))" /> Si el curso ya está en el grado, actualizar sus horas</label>
              <button type="button" class="btn btn-primary" [disabled]="!puedeAplicarMalla() || aplicando()" (click)="aplicarMalla()">
                {{ aplicando() ? 'Aplicando…' : 'Asignar ' + cursosElegidos().size + ' curso(s) a ' + gradosElegidos().size + ' grado(s)' }}
              </button>
            </div>
          }
        </div>
      </section>

      <!-- 2. Preparación -->
      <section class="card" aria-labelledby="t-prep">
        <div class="card-body">
          <div class="sec-cab">
            <h2 id="t-prep" class="sec-titulo">2. ¿Qué grados están listos?</h2>
            <span class="badge badge-ok">{{ listas().length }} listas de {{ filas().length }}</span>
          </div>
          <p class="muted" style="margin: 0 0 10px">Una sección está lista cuando su grado tiene cursos, cada curso tiene docente, el turno tiene bloques y las horas caben en la semana. Marca las que quieres armar.</p>
          <div class="toolbar">
            <button type="button" class="btn btn-sm" (click)="seleccionarListas()">Marcar las listas</button>
            @for (n of niveles(); track n.id) { <button type="button" class="btn btn-sm" (click)="seleccionarNivel(n.id)">Todas de {{ n.nombre }}</button> }
            <button type="button" class="btn btn-sm" (click)="seleccion.set(vacio)">Quitar marcas</button>
          </div>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th></th><th>Sección</th><th>Cursos / horas</th><th>Docentes</th><th>Clases ya armadas</th><th>Estado</th></tr></thead>
            <tbody>
              @for (f of filas(); track f.seccionId) {
                <tr>
                  <td><input type="checkbox" [checked]="seleccion().has(f.seccionId)" (change)="alternarSeccion(f.seccionId)" [attr.aria-label]="'Elegir ' + f.nombre" /></td>
                  <td><strong>{{ f.nombre }}</strong><br /><span class="muted">{{ f.aula ?? 'Sin aula' }} · {{ f.turno === 'MANANA' ? 'mañana' : 'tarde' }}</span></td>
                  <td>{{ f.cursosMalla }} cursos · {{ f.horasMalla }}/{{ f.espacios }} h</td>
                  <td>@if (f.sinDocente.length === 0) { <span class="badge badge-ok">Completos</span> } @else { <span class="badge badge-warn">Faltan {{ f.sinDocente.length }}</span> }</td>
                  <td>{{ f.clasesProgramadas }}</td>
                  <td>
                    @if (f.lista) { <span class="badge badge-ok">Lista</span> } @else {
                      <span class="badge badge-warn">Le falta</span>
                      <ul class="problemas">@for (p of f.problemas; track p) { <li>{{ p }}</li> }</ul>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="muted">{{ cargando() ? 'Cargando…' : 'No hay secciones activas en este año.' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <!-- 3. Generar -->
      <section class="card" aria-labelledby="t-gen">
        <div class="card-body">
          <div class="sec-cab"><h2 id="t-gen" class="sec-titulo">3. Armar los horarios</h2><span class="badge badge-gray">{{ seleccion().size }} sección(es) marcadas</span></div>
          <div class="opciones">
            <label class="check"><input type="checkbox" [checked]="asignarDocentes()" (change)="asignarDocentes.set(marcado($event))" />
              Asignar docentes automáticamente a los cursos que no tengan (según su especialidad y su carga)</label>
            <label class="check"><input type="checkbox" [checked]="reemplazar()" (change)="reemplazar.set(marcado($event))" />
              Reemplazar las clases que ya están armadas (si no, solo se completa lo que falta)</label>
          </div>
          <div class="toolbar" style="margin-top: 12px">
            <button type="button" class="btn" [disabled]="seleccion().size === 0 || trabajando()" (click)="generar(true)">{{ trabajando() === 'simular' ? 'Simulando…' : 'Simular (no guarda)' }}</button>
            <button type="button" class="btn btn-primary" [disabled]="seleccion().size === 0 || trabajando()" (click)="generar(false)">{{ trabajando() === 'generar' ? 'Generando…' : 'Generar horarios' }}</button>
          </div>

          @if (resultado(); as r) {
            <div [class]="'aviso ' + (r.horasSinColocar === 0 ? 'aviso-ok' : 'aviso-warn')" style="margin-top: 14px" role="status">
              {{ r.simulacion ? 'Simulación (no se guardó nada): ' : 'Listo: ' }}{{ r.horasColocadas }} hora(s) ubicadas en {{ r.seccionesGeneradas }} sección(es)
              @if (r.horasSinColocar > 0) { · <strong>{{ r.horasSinColocar }} sin lugar</strong> }
              @if (r.docentesAsignados > 0) { · {{ r.docentesAsignados }} docente(s) asignados a cursos }
              @if (r.seccionesOmitidas > 0) { · {{ r.seccionesOmitidas }} omitida(s) por no estar listas }
            </div>
            <div class="table-wrap">
              <table class="table">
                <thead><tr><th>Sección</th><th>Resultado</th><th>Horas</th><th>Detalle</th><th></th></tr></thead>
                <tbody>
                  @for (s of r.secciones; track s.seccionId) {
                    <tr>
                      <td><strong>{{ s.nombre }}</strong></td>
                      <td><span [class]="'badge ' + claseEstado(s.estado)">{{ textoEstado(s.estado) }}</span></td>
                      <td>{{ s.colocadas }}/{{ s.requeridas }}</td>
                      <td class="muted"><ul class="problemas">@for (m of s.mensajes; track m) { <li>{{ m }}</li> }</ul></td>
                      <td class="acciones">@if (!r.simulacion && s.estado !== 'OMITIDO') { <a class="btn btn-sm" routerLink="/planificacion/horarios" [queryParams]="{ seccion: s.seccionId }">Ver horario</a> }</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (r.simulacion) {
              <p class="muted" style="margin-top: 10px">Si el resultado te convence, pulsa «Generar horarios»: queda exactamente igual a esta simulación.</p>
            } @else {
              <p class="muted" style="margin-top: 10px">Revisa cada horario y publícalo desde «Horarios por sección» para avisar a docentes y familias.</p>
            }
          }
        </div>
      </section>
    </div>
  `
})
export class HorarioAutoComponent implements OnInit {
  private readonly api = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  protected readonly esAdmin = inject(AuthService).isAdmin;

  protected readonly vacio = new Set<number>();
  protected readonly anios = signal<Anio[]>([]);
  protected readonly anioId = signal(0);
  protected readonly grados = signal<Grado[]>([]);
  protected readonly cursos = signal<Curso[]>([]);
  protected readonly filas = signal<PreparacionSeccion[]>([]);
  protected readonly cargando = signal(true);

  protected readonly gradosElegidos = signal<Set<number>>(new Set());
  protected readonly cursosElegidos = signal<Map<number, number>>(new Map());
  protected readonly sobrescribir = signal(false);
  protected readonly aplicando = signal(false);

  protected readonly seleccion = signal<Set<number>>(new Set());
  protected readonly asignarDocentes = signal(true);
  protected readonly reemplazar = signal(false);
  protected readonly trabajando = signal<'' | 'simular' | 'generar'>('');
  protected readonly resultado = signal<ResultadoGeneracion | null>(null);

  protected readonly valor = valor;
  protected readonly marcado = marcado;

  protected readonly niveles = computed(() => {
    const m = new Map<number, string>();
    for (const g of this.grados()) m.set(g.nivelId, g.nivelNombre);
    return [...m].map(([id, nombre]) => ({ id, nombre }));
  });
  protected readonly listas = computed(() => this.filas().filter(f => f.lista));
  protected readonly puedeAplicarMalla = computed(() => this.gradosElegidos().size > 0 && this.cursosElegidos().size > 0);

  ngOnInit(): void {
    this.api.grados().subscribe(g => this.grados.set(g.filter(x => x.activo)));
    this.api.cursos().subscribe(c => this.cursos.set(c.filter(x => x.activo)));
    this.api.anios().subscribe(a => {
      this.anios.set(a);
      this.anioId.set((a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0);
      this.cargarPreparacion();
    });
  }

  protected numero(e: Event): number {
    return Number(valor(e));
  }

  protected cambiarAnio(id: number): void {
    this.anioId.set(id);
    this.resultado.set(null);
    this.cargarPreparacion();
  }

  protected cargarPreparacion(): void {
    if (!this.anioId()) return;
    this.cargando.set(true);
    this.api.preparacionHorarios(this.anioId()).subscribe({
      next: f => {
        this.filas.set(f);
        const existentes = new Set(f.map(x => x.seccionId));
        this.seleccion.update(s => new Set([...s].filter(id => existentes.has(id))));
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo leer el estado de las secciones.'));
      }
    });
  }

  // ---------------------------------------------------------------- 1. malla en lote
  protected resumenGrado(gradoId: number): string {
    const fila = this.filas().find(f => f.gradoId === gradoId);
    return !fila ? 'sin secciones' : fila.cursosMalla === 0 ? 'sin cursos' : `${fila.cursosMalla} cursos · ${fila.horasMalla} h`;
  }

  protected nivelCompleto(nivelId: number): boolean {
    const ids = this.grados().filter(g => g.nivelId === nivelId).map(g => g.id);
    return ids.length > 0 && ids.every(id => this.gradosElegidos().has(id));
  }

  protected alternarNivel(nivelId: number): void {
    const ids = this.grados().filter(g => g.nivelId === nivelId).map(g => g.id);
    const todos = this.nivelCompleto(nivelId);
    this.gradosElegidos.update(s => {
      const n = new Set(s);
      ids.forEach(id => (todos ? n.delete(id) : n.add(id)));
      return n;
    });
  }

  protected alternarGrado(id: number): void {
    this.gradosElegidos.update(s => {
      const n = new Set(s);
      if (!n.delete(id)) n.add(id);
      return n;
    });
  }

  protected alternarCurso(id: number): void {
    this.cursosElegidos.update(m => {
      const n = new Map(m);
      if (!n.delete(id)) n.set(id, 2);
      return n;
    });
  }

  protected cambiarHoras(id: number, horas: number): void {
    this.cursosElegidos.update(m => new Map(m).set(id, Math.min(40, Math.max(1, Math.trunc(horas) || 1))));
  }

  protected async aplicarMalla(): Promise<void> {
    const grados = [...this.gradosElegidos()];
    const cursos = [...this.cursosElegidos()].map(([cursoId, horasSemanales]) => ({ cursoId, horasSemanales }));
    const nombres = this.grados().filter(g => this.gradosElegidos().has(g.id)).map(g => g.nombre).join(', ');
    if (!(await this.confirmacion.confirmar(`Se agregarán ${cursos.length} curso(s) a: ${nombres}.`, 'Asignar'))) return;
    this.aplicando.set(true);
    this.api.agregarMallaLote({ gradoIds: grados, cursos, sobrescribir: this.sobrescribir() }).subscribe({
      next: r => {
        this.aplicando.set(false);
        this.avisos.success(`Malla actualizada: ${r.creados} agregado(s), ${r.actualizados} actualizado(s), ${r.omitidos} ya estaban.`);
        this.cargarPreparacion();
      },
      error: e => {
        this.aplicando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron asignar los cursos.'));
      }
    });
  }

  // ---------------------------------------------------------------- 2. selección de secciones
  protected alternarSeccion(id: number): void {
    this.seleccion.update(s => {
      const n = new Set(s);
      if (!n.delete(id)) n.add(id);
      return n;
    });
  }

  protected seleccionarListas(): void {
    this.seleccion.set(new Set(this.listas().map(f => f.seccionId)));
  }

  protected seleccionarNivel(nivelId: number): void {
    this.seleccion.set(new Set(this.filas().filter(f => f.nivelId === nivelId).map(f => f.seccionId)));
  }

  // ---------------------------------------------------------------- 3. generar
  protected async generar(simular: boolean): Promise<void> {
    if (!simular && this.reemplazar()) {
      const ok = await this.confirmacion.confirmar(
        `Se borrarán las clases ya armadas de las ${this.seleccion().size} sección(es) marcadas y se volverán a generar.`, 'Reemplazar', true);
      if (!ok) return;
    }
    this.trabajando.set(simular ? 'simular' : 'generar');
    this.api
      .generarHorarios({ anioId: this.anioId(), seccionIds: [...this.seleccion()], reemplazar: this.reemplazar(), asignarDocentes: this.asignarDocentes(), simular })
      .subscribe({
        next: r => {
          this.trabajando.set('');
          this.resultado.set(r);
          if (!simular) {
            this.avisos.success(r.horasSinColocar === 0 ? 'Horarios generados sin cruces.' : 'Horarios generados; quedaron horas sin lugar (ver el detalle).');
            this.cargarPreparacion();
          }
        },
        error: e => {
          this.trabajando.set('');
          this.avisos.error(mensajeError(e, 'No se pudieron generar los horarios.'));
        }
      });
  }

  protected claseEstado(e: string): string {
    return e === 'GENERADO' ? 'badge-ok' : e === 'INCOMPLETO' ? 'badge-danger' : e === 'OMITIDO' ? 'badge-warn' : 'badge-gray';
  }

  protected textoEstado(e: string): string {
    return e === 'GENERADO' ? 'Generado' : e === 'INCOMPLETO' ? 'Incompleto' : e === 'OMITIDO' ? 'Omitido' : 'Sin cambios';
  }
}
