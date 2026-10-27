import { Component, ViewEncapsulation, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { COLEGIO } from './datos';

/** Marco del sitio público: cabecera, pie y botón "Ingresar al portal". No exige sesión. */
@Component({
  selector: 'app-publico-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './publico-layout.component.html',
  styleUrl: './publico.css',
  encapsulation: ViewEncapsulation.None
})
export class PublicoLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly kc = inject(KeycloakService);
  private readonly router = inject(Router);

  protected readonly colegio = COLEGIO;
  protected readonly anio = new Date().getFullYear();
  protected readonly menuAbierto = signal(false);
  protected readonly conSesion = this.auth.isAuthenticated;
  protected readonly enlaces = [
    { ruta: '/', etiqueta: 'Inicio', exacto: true },
    { ruta: '/nosotros', etiqueta: 'Nosotros', exacto: false },
    { ruta: '/niveles', etiqueta: 'Niveles', exacto: false },
    { ruta: '/admision', etiqueta: 'Admisión', exacto: false },
    { ruta: '/noticias', etiqueta: 'Noticias', exacto: false },
    { ruta: '/contacto', etiqueta: 'Contacto', exacto: false }
  ];

  constructor() {
    // Al cambiar de página se vuelve arriba y se cierra el menú del celular.
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed()).subscribe(() => {
      this.menuAbierto.set(false);
      window.scrollTo({ top: 0 });
    });
  }

  /** Con Keycloak el login es de su servidor; con el login propio, la pantalla /login. */
  protected ingresar(): void {
    if (this.conSesion()) {
      void this.router.navigate(['/portal']);
    } else if (this.auth.esKeycloak) {
      void this.kc.login(window.location.origin + '/portal');
    } else {
      void this.router.navigate(['/login']);
    }
  }
}
