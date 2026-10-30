import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { timer } from 'rxjs';
import { catchError, filter, map, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { AlumnoService } from '../../core/services/alumno.service';
import { AlumnoResumen } from '../../core/models/alumno.model';
import { AuthService } from '../../core/services/auth.service';
import { NotificacionService } from '../../core/services/notificacion.service';
import { NAV_AULA, usaBarraAula } from '../aula-nav';
import { HUBS, hubDe } from '../hubs';
import { MENU, PERSONAL } from '../menu';
import { AvisoServicioComponent } from '../../shared/components/aviso-servicio/aviso-servicio.component';

@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterOutlet, AvisoServicioComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificaciones = inject(NotificacionService);
  private readonly alumnosApi = inject(AlumnoService);
  protected readonly noLeidas = this.notificaciones.noLeidas;

  protected readonly menuAbierto = signal(false);
  protected readonly usuario = this.auth.currentUser;

  /** Solo los grupos con al menos una opción permitida para los roles de la sesión. */
  protected readonly menu = computed(() =>
    MENU.map(g => ({
      ...g,
      items: g.items.filter(i => this.auth.tieneAlgunRol(i.roles) && !(i.soloEduspace && this.auth.esKeycloak))
    })).filter(g => g.items.length > 0)
  );

  /** URL actual (sin parámetros) para saber a qué espacio de trabajo pertenece la pantalla. */
  private readonly url = toSignal(
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), map(() => this.router.url)),
    { initialValue: this.router.url }
  );

  private readonly pestanaVisible = (p: { roles: string[]; soloEduspace?: boolean }) =>
    this.auth.tieneAlgunRol(p.roles) && !(p.soloEduspace && this.auth.esKeycloak);

  /** Espacio de trabajo de la pantalla abierta, con solo las pestañas que el rol puede usar. */
  protected readonly hubActual = computed(() => {
    const h = hubDe(this.url());
    if (!h) return null;
    return { hub: h.hub, activa: h.pestana.ruta, pestanas: h.hub.pestanas.filter(this.pestanaVisible) };
  });

  /** Un ítem suelto se resalta en su ruta; la entrada de un espacio de trabajo, en cualquiera de sus pestañas. */
  protected esActivo(i: { ruta: string; hub?: string }): boolean {
    const h = this.hubActual();
    if (i.hub) return h?.hub.id === i.hub;
    if (h) return false;
    const u = this.url().split(/[?#]/)[0];
    return u === i.ruta || u.startsWith(i.ruta + '/');
  }

  // --- Barra superior de docentes, alumnos y apoderados (sin menú lateral) ---
  protected readonly esAula = computed(() => usaBarraAula(this.auth.roles()));
  protected readonly navAula = computed(() => NAV_AULA.filter(e => this.auth.tieneAlgunRol(e.roles)));
  protected readonly menuUsuario = signal(false);
  protected readonly iniciales = computed(() => (this.usuario()?.username ?? '?').slice(0, 2).toUpperCase());

  protected activoAula(e: { prefijos: string[] }): boolean {
    const u = this.url().split(/[?#]/)[0];
    return e.prefijos.some(p => u === p || u.startsWith(p + '/'));
  }

  protected cerrarMenuUsuario(): void {
    this.menuUsuario.set(false);
  }

  // --- Paleta de búsqueda (Ctrl+K): saltar a cualquier pantalla permitida o abrir el expediente de un alumno ---
  protected readonly paletaAbierta = signal(false);
  protected readonly texto = signal('');
  protected readonly alumnos = signal<AlumnoResumen[]>([]);
  protected readonly seleccion = signal(0);
  protected readonly puedeBuscarAlumnos = computed(() => this.auth.tieneAlgunRol(PERSONAL));
  private busqueda?: ReturnType<typeof setTimeout>;

  protected readonly opciones = computed(() => {
    const t = this.texto().trim().toLowerCase();
    // Cada pestaña de un espacio de trabajo es una pantalla propia en la búsqueda; la entrada del hub sería un duplicado de su primera pestaña.
    const sueltas = this.menu().flatMap(g => g.items.filter(i => !i.hub).map(i => ({ tipo: 'pantalla' as const, etiqueta: i.etiqueta, detalle: g.titulo, ruta: i.ruta })));
    const deHubs = HUBS.flatMap(h => h.pestanas.filter(this.pestanaVisible).map(p => ({ tipo: 'pantalla' as const, etiqueta: p.etiqueta, detalle: h.titulo, ruta: p.ruta })));
    const pantallas = [...sueltas, ...deHubs]
      .filter(o => !t || `${o.etiqueta} ${o.detalle}`.toLowerCase().includes(t));
    const personas = this.alumnos().map(a => ({ tipo: 'alumno' as const, etiqueta: a.nombreCompleto, detalle: `Alumno · ${a.numeroDocumento}`, ruta: `/alumnos/${a.id}` }));
    return [...personas, ...pantallas].slice(0, 12);
  });

  protected readonly esKeycloak = this.auth.esKeycloak;
  protected readonly consolaAdmin = this.auth.consolaAdminUrl;
  protected readonly esAdmin = this.auth.isAdmin;

  protected readonly rolPrincipal = computed(() => this.auth.roles().join(' · '));

  constructor() {
    // Contador de notificaciones sin leer: al entrar y luego cada minuto. Si falla, el menú sigue funcionando.
    timer(0, 60_000)
      .pipe(
        switchMap(() => this.notificaciones.actualizarConteo().pipe(catchError(() => of(0)))),
        takeUntilDestroyed()
      )
      .subscribe();
  }

  @HostListener('document:keydown', ['$event'])
  protected alTeclear(e: KeyboardEvent): void {
    if (e.key === 'Escape' && this.menuUsuario()) this.menuUsuario.set(false);
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      this.paletaAbierta() ? this.cerrarPaleta() : this.abrirPaleta();
    }
  }

  protected abrirPaleta(): void {
    this.texto.set('');
    this.alumnos.set([]);
    this.seleccion.set(0);
    this.paletaAbierta.set(true);
    setTimeout(() => document.getElementById('paleta-texto')?.focus());
  }

  protected cerrarPaleta(): void {
    this.paletaAbierta.set(false);
  }

  protected escribir(valor: string): void {
    this.texto.set(valor);
    this.seleccion.set(0);
    clearTimeout(this.busqueda);
    const q = valor.trim();
    if (q.length < 2 || !this.puedeBuscarAlumnos()) {
      this.alumnos.set([]);
      return;
    }
    this.busqueda = setTimeout(() => {
      this.alumnosApi.listar(q, '', 0, 5).pipe(catchError(() => of(null))).subscribe(p => {
        if (this.texto().trim() === q) this.alumnos.set(p?.contenido ?? []);
      });
    }, 250);
  }

  protected teclaPaleta(e: KeyboardEvent): void {
    const n = this.opciones().length;
    if (e.key === 'ArrowDown') { e.preventDefault(); this.seleccion.update(i => (i + 1) % Math.max(n, 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); this.seleccion.update(i => (i - 1 + Math.max(n, 1)) % Math.max(n, 1)); }
    else if (e.key === 'Enter') { e.preventDefault(); const o = this.opciones()[this.seleccion()]; if (o) this.irA(o.ruta); }
    else if (e.key === 'Escape') this.cerrarPaleta();
  }

  protected irA(ruta: string): void {
    this.cerrarPaleta();
    this.router.navigateByUrl(ruta);
  }

  protected alternarMenu(): void {
    this.menuAbierto.update(v => !v);
  }

  protected cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  protected salir(): void {
    this.auth.logout();
  }

  protected irACambiarClave(): void {
    if (this.esKeycloak) {
      this.auth.abrirCuenta();
      return;
    }
    this.router.navigate(['/cambiar-clave']);
  }
}
