import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Observable, concatMap, forkJoin, from, of, toArray } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { Page } from '../../core/models/page.model';
import { Usuario } from '../../core/models/usuario.model';
import { AlumnoService } from '../../core/services/alumno.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { CuentasService, PersonaCuenta, TipoPersona, partirNombre } from '../../core/services/cuentas.service';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

type Pestana = TipoPersona | 'PERSONAL';
type Estado = 'SIN_CUENTA' | 'PRIMER_INGRESO' | 'ACTIVA' | 'BLOQUEADA' | 'INACTIVA';

interface Fila {
  persona: PersonaCuenta | null; // null en «Personal» (cuentas sin ficha de persona)
  nombre: string;
  documento: string;
  usuario: Usuario | null;
}

const TAMANO = 100;
const ETIQUETA: Record<Estado, string> = {
  SIN_CUENTA: 'Sin cuenta', PRIMER_INGRESO: 'Aún no cambia su clave', ACTIVA: 'Activa', BLOQUEADA: 'Bloqueada', INACTIVA: 'Desactivada'
};
const BADGE: Record<Estado, string> = {
  SIN_CUENTA: 'badge-gray', PRIMER_INGRESO: 'badge-warn', ACTIVA: 'badge-ok', BLOQUEADA: 'badge-danger', INACTIVA: 'badge-gray'
};

/** Trae todas las páginas de un listado (los colegios chicos caben en una o dos). */
function todas<T>(pagina: (n: number) => Observable<Page<T>>): Observable<T[]> {
  return pagina(0).pipe(
    concatMap(p => {
      if (p.totalPaginas <= 1) return of(p.contenido);
      const resto = Array.from({ length: p.totalPaginas - 1 }, (_, i) => pagina(i + 1));
      return forkJoin(resto).pipe(map(r => [...p.contenido, ...r.flatMap(x => x.contenido)]));
    })
  );
}

/**
 * Cuentas de acceso de las personas del colegio. Se crean con usuario «nombre.apellido» y clave inicial igual al DNI
 * (cambio obligatorio al entrar); aquí también se restauran si alguien olvida su clave. Las cuentas ADMIN no se tocan desde aquí.
 */
@Component({
  selector: 'app-cuentas',
  imports: [PageHeaderComponent, ModalComponent],
  template: `
    <app-page-header titulo="Cuentas de acceso" subtitulo="Crea las cuentas de alumnos, docentes y apoderados, y restaura la clave de quien la olvidó.">
      @if (pendientes().length > 0) {
        <button type="button" class="btn btn-primary" [disabled]="trabajando()" (click)="crearFaltantes()">Crear {{ pendientes().length }} cuenta(s) faltantes</button>
      }
    </app-page-header>

    <div class="aviso" style="margin-bottom: 14px">
      <strong>Cómo ingresan:</strong> su usuario es <em>nombre.apellido</em> (por ejemplo <code>maria.aguilar</code>) y su clave inicial es su <strong>documento de identidad</strong>.
      Al entrar por primera vez el sistema les pide cambiarla. Si la olvidan, usa «Restaurar» y volverá a ser su documento.
    </div>

    <div class="toolbar" style="margin-bottom: 12px">
      <div class="chips" role="tablist" aria-label="Tipo de cuenta">
        @for (p of pestanas; track p.id) {
          <button type="button" role="tab" class="chip" [class.on]="pestana() === p.id" [attr.aria-selected]="pestana() === p.id" (click)="elegir(p.id)">{{ p.etiqueta }}</button>
        }
      </div>
      <input class="input grow" type="search" placeholder="Buscar por nombre, documento o usuario…" aria-label="Buscar" [value]="texto()" (input)="texto.set($any($event.target).value)" />
      <select class="input" aria-label="Estado" (change)="filtro.set($any($event.target).value)">
        <option value="" [selected]="filtro() === ''">Todos los estados</option>
        @for (e of estados; track e) { <option [value]="e" [selected]="filtro() === e">{{ etiqueta(e) }}</option> }
      </select>
    </div>

    <div class="mini-stats" style="margin-bottom: 12px">
      <div class="mini"><span class="mini-v">{{ filas().length }}</span><span class="muted">en total</span></div>
      <div class="mini"><span class="mini-v">{{ contar('ACTIVA') }}</span><span class="muted">activas</span></div>
      <div class="mini" [class.alerta]="contar('PRIMER_INGRESO') > 0"><span class="mini-v">{{ contar('PRIMER_INGRESO') }}</span><span class="muted">aún no cambian su clave</span></div>
      @if (pestana() !== 'PERSONAL') {
        <div class="mini" [class.alerta]="contar('SIN_CUENTA') > 0"><span class="mini-v">{{ contar('SIN_CUENTA') }}</span><span class="muted">sin cuenta</span></div>
      }
    </div>

    @if (cargando()) {
      <p class="muted">Cargando…</p>
    } @else {
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>Nombre</th><th>Documento</th><th>Usuario</th><th>Estado</th><th>Último ingreso</th><th></th></tr></thead>
          <tbody>
            @for (f of visibles(); track clave(f)) {
              <tr>
                <td>{{ f.nombre }}</td>
                <td>{{ f.documento || '—' }}</td>
                <td>@if (f.usuario) { <code>{{ f.usuario.username }}</code> } @else { <span class="muted">—</span> }</td>
                <td><span [class]="'badge ' + badge(estadoDe(f))">{{ etiqueta(estadoDe(f)) }}</span></td>
                <td>{{ f.usuario?.ultimoLogin ? fecha(f.usuario!.ultimoLogin!) : '—' }}</td>
                <td class="acciones">
                  @if (!f.usuario) {
                    <button type="button" class="btn btn-sm btn-primary" [disabled]="trabajando() || !f.documento" [title]="f.documento ? '' : 'Falta el documento de la persona'" (click)="crear(f)">Crear cuenta</button>
                  } @else {
                    @if (f.persona) {
                      <button type="button" class="btn btn-sm" [disabled]="trabajando()" (click)="restaurar(f)">Restaurar</button>
                    } @else {
                      <button type="button" class="btn btn-sm" [disabled]="trabajando()" (click)="restaurarTemporal(f)">Nueva clave</button>
                    }
                    @if (f.usuario.bloqueado) { <button type="button" class="btn btn-sm" [disabled]="trabajando()" (click)="desbloquear(f)">Desbloquear</button> }
                    <button type="button" class="btn btn-sm" [disabled]="trabajando()" (click)="alternar(f)">{{ f.usuario.activo ? 'Desactivar' : 'Activar' }}</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="muted">No hay resultados.</td></tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (resultado(); as r) {
      <app-modal [titulo]="r.titulo" [ancho]="520" (cerrar)="resultado.set(null)">
        @for (l of r.lineas; track l) { <p style="margin: 0 0 8px">{{ l }}</p> }
        @if (r.usuario) {
          <dl class="datos">
            <div><dt>Usuario</dt><dd><code>{{ r.usuario }}</code></dd></div>
            <div><dt>Clave inicial</dt><dd>{{ r.clave }}</dd></div>
          </dl>
        }
        <ng-container footer><button type="button" class="btn btn-primary" (click)="resultado.set(null)">Entendido</button></ng-container>
      </app-modal>
    }
  `
})
export class CuentasComponent implements OnInit {
  private readonly alumnos = inject(AlumnoService);
  private readonly plan = inject(PlanificacionService);
  private readonly usuarios = inject(UsuarioService);
  private readonly cuentas = inject(CuentasService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);

  protected readonly pestanas: { id: Pestana; etiqueta: string }[] = [
    { id: 'ALUMNO', etiqueta: 'Alumnos' }, { id: 'DOCENTE', etiqueta: 'Docentes' }, { id: 'APODERADO', etiqueta: 'Apoderados' }, { id: 'PERSONAL', etiqueta: 'Secretaría y caja' }
  ];
  protected readonly estados: Estado[] = ['SIN_CUENTA', 'PRIMER_INGRESO', 'ACTIVA', 'BLOQUEADA', 'INACTIVA'];
  protected readonly pestana = signal<Pestana>('ALUMNO');
  protected readonly texto = signal('');
  protected readonly filtro = signal('');
  protected readonly filas = signal<Fila[]>([]);
  protected readonly cargando = signal(true);
  protected readonly trabajando = signal(false);
  protected readonly resultado = signal<{ titulo: string; lineas: string[]; usuario?: string; clave?: string } | null>(null);

  protected readonly etiqueta = (e: Estado) => ETIQUETA[e];
  protected readonly badge = (e: Estado) => BADGE[e];
  protected readonly clave = (f: Fila) => `${f.persona?.tipo ?? 'P'}${f.persona?.id ?? f.usuario?.id}`;

  protected readonly visibles = computed(() => {
    const t = this.texto().trim().toLowerCase();
    const e = this.filtro();
    return this.filas().filter(f => (!e || this.estadoDe(f) === e) && (!t || `${f.nombre} ${f.documento} ${f.usuario?.username ?? ''}`.toLowerCase().includes(t)));
  });
  protected readonly pendientes = computed(() => (this.pestana() === 'PERSONAL' ? [] : this.filas().filter(f => !f.usuario && f.documento)));

  ngOnInit(): void {
    this.cargar();
  }

  protected elegir(p: Pestana): void {
    this.pestana.set(p);
    this.filtro.set('');
    this.cargar();
  }

  protected estadoDe(f: Fila): Estado {
    const u = f.usuario;
    if (!u) return 'SIN_CUENTA';
    if (!u.activo) return 'INACTIVA';
    if (u.bloqueado) return 'BLOQUEADA';
    return u.debeCambiarClave ? 'PRIMER_INGRESO' : 'ACTIVA';
  }

  protected contar(e: Estado): number {
    return this.filas().filter(f => this.estadoDe(f) === e).length;
  }

  protected fecha(iso: string): string {
    return new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
  }

  private cargar(): void {
    this.cargando.set(true);
    const tipo = this.pestana();
    const roles = tipo === 'PERSONAL' ? ['SECRETARIA', 'CAJERO'] : [tipo];
    const cuentas$ = forkJoin(roles.map(r => todas(n => this.usuarios.listar({ rol: r, page: n, size: TAMANO })))).pipe(map(x => x.flat()));
    const personas$: Observable<PersonaCuenta[]> =
      tipo === 'ALUMNO'
        ? todas(n => this.alumnos.listar('', 'ACTIVO', n, TAMANO)).pipe(map(l => l.map(a => ({ tipo: 'ALUMNO' as const, id: a.id, ...partirNombre(a.nombreCompleto), documento: a.numeroDocumento, usuarioId: a.usuarioId }))))
        : tipo === 'DOCENTE'
          ? todas(n => this.plan.docentes('', n, TAMANO)).pipe(map(l => l.filter(d => d.estado === 'ACTIVO').map(d => ({ tipo: 'DOCENTE' as const, id: d.id, nombres: d.nombres, apellidos: d.apellidos, documento: d.numeroDocumento, usuarioId: d.usuarioId }))))
          : tipo === 'APODERADO'
            ? todas(n => this.alumnos.buscarApoderados('', n, TAMANO)).pipe(map(l => l.map(a => ({ tipo: 'APODERADO' as const, id: a.id, nombres: a.nombres, apellidos: a.apellidos, documento: a.numeroDocumento, usuarioId: a.usuarioId }))))
            : of([]);
    forkJoin({ cuentas: cuentas$.pipe(catchError(() => of([] as Usuario[]))), personas: personas$ }).subscribe({
      next: ({ cuentas, personas }) => {
        const porId = new Map(cuentas.map(u => [u.id, u]));
        this.filas.set(
          tipo === 'PERSONAL'
            ? cuentas.filter(u => !u.roles.includes('ADMIN')).map(u => ({ persona: null, nombre: `${u.apellidos}, ${u.nombres}`, documento: '', usuario: u }))
            : personas.map(p => ({ persona: p, nombre: `${p.apellidos}, ${p.nombres}`, documento: p.documento, usuario: (p.usuarioId && porId.get(p.usuarioId)) || null }))
        );
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar las cuentas.'));
      }
    });
  }

  protected crear(f: Fila): void {
    this.trabajando.set(true);
    this.cuentas.crear(f.persona!).subscribe({
      next: u => {
        this.trabajando.set(false);
        this.resultado.set({ titulo: 'Cuenta creada', lineas: [`${f.nombre} ya puede ingresar. Al entrar por primera vez tendrá que cambiar su clave.`], usuario: u.username, clave: `su documento (${f.documento})` });
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo crear la cuenta.'));
        this.cargar(); // si la cuenta se creó pero no se vinculó, se verá el estado real
      }
    });
  }

  protected async crearFaltantes(): Promise<void> {
    const lista = this.pendientes();
    if (!(await this.confirmacion.confirmar(`¿Crear ${lista.length} cuenta(s)? Cada una usará nombre.apellido como usuario y el documento como clave inicial.`, 'Crear cuentas'))) return;
    this.trabajando.set(true);
    let fallos = 0;
    from(lista)
      .pipe(
        concatMap(f => this.cuentas.crear(f.persona!).pipe(map(() => true), catchError(() => { fallos++; return of(false); }))),
        toArray()
      )
      .subscribe(r => {
        this.trabajando.set(false);
        const ok = r.filter(Boolean).length;
        this.resultado.set({ titulo: 'Cuentas creadas', lineas: [`${ok} cuenta(s) creada(s)${fallos ? ` y ${fallos} con error` : ''}.`, 'Usuario: nombre.apellido · Clave inicial: documento. Cada persona debe cambiarla al entrar.'] });
        this.cargar();
      });
  }

  protected async restaurar(f: Fila): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Restaurar la cuenta de ${f.nombre}? Su clave volverá a ser su documento y deberá cambiarla al entrar.`, 'Restaurar'))) return;
    this.trabajando.set(true);
    this.cuentas.restablecer({ ...f.persona!, usuarioId: f.usuario!.id }).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.resultado.set({ titulo: 'Cuenta restaurada', lineas: [`${f.nombre} puede entrar de nuevo. Se le pedirá cambiar la clave.`], usuario: f.usuario!.username, clave: `su documento (${f.documento})` });
        this.cargar();
      },
      error: e => this.fallo(e, 'No se pudo restaurar la cuenta.')
    });
  }

  /** Secretaría y caja no tienen documento en su ficha: se genera una clave temporal que se muestra una sola vez. */
  protected async restaurarTemporal(f: Fila): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Generar una clave temporal para ${f.nombre}? Deberá cambiarla al entrar.`, 'Generar'))) return;
    this.trabajando.set(true);
    this.usuarios.restablecerClave(f.usuario!.id).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.resultado.set({ titulo: 'Clave temporal', lineas: ['Entrégala a la persona; no se vuelve a mostrar.'], usuario: r.usuario.username, clave: r.claveTemporal });
        this.cargar();
      },
      error: e => this.fallo(e, 'No se pudo generar la clave.')
    });
  }

  protected desbloquear(f: Fila): void {
    this.accion(this.usuarios.desbloquear(f.usuario!.id), 'Cuenta desbloqueada.');
  }

  protected alternar(f: Fila): void {
    this.accion(f.usuario!.activo ? this.usuarios.desactivar(f.usuario!.id) : this.usuarios.activar(f.usuario!.id), f.usuario!.activo ? 'Cuenta desactivada.' : 'Cuenta activada.');
  }

  private accion(obs: Observable<Usuario>, mensaje: string): void {
    this.trabajando.set(true);
    obs.subscribe({
      next: () => {
        this.trabajando.set(false);
        this.avisos.success(mensaje);
        this.cargar();
      },
      error: e => this.fallo(e, 'No se pudo completar la acción.')
    });
  }

  private fallo(e: unknown, texto: string): void {
    this.trabajando.set(false);
    this.avisos.error(mensajeError(e, texto));
  }
}
