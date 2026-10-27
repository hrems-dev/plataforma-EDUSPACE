import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { modoGuard } from '../guards/modo.guard';
import { jwtFalso } from '../testing/jwt-falso';
import { AuthService } from './auth.service';
import { KeycloakService } from './keycloak.service';

/** Keycloak simulado: el token "actual" cambia cuando se renueva. */
function keycloakFalso(roles: string[]) {
  const estado = { token: jwtFalso(roles) };
  return {
    estado,
    autenticado: true,
    get token() { return estado.token; },
    consolaAdminUrl: 'http://kc/admin/eduspace/console/',
    actualizarToken: vi.fn(async (min: number) => {
      if (min === -1) estado.token = jwtFalso(roles, 7200);
      return true;
    }),
    logout: vi.fn(async () => undefined),
    cuenta: vi.fn(async () => undefined),
    login: vi.fn(async () => undefined)
  };
}

function entorno(roles = ['ADMIN']) {
  const kc = keycloakFalso(roles);
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: APP_CONFIG, useValue: { authMode: 'keycloak', keycloak: { url: 'http://kc', realm: 'eduspace', clientId: 'eduspace-web' } } },
      { provide: KeycloakService, useValue: kc }
    ]
  });
  return { kc, auth: TestBed.inject(AuthService), http: TestBed.inject(HttpClient), ctrl: TestBed.inject(HttpTestingController) };
}

describe('AuthService en modo Keycloak', () => {
  it('toma usuario y roles del token de Keycloak, sin sessionStorage', () => {
    sessionStorage.clear();
    const { auth } = entorno(['ADMIN', 'SECRETARIA']);
    expect(auth.esKeycloak).toBe(true);
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.currentUser()?.id).toBe(7);
    expect(auth.tieneAlgunRol(['SECRETARIA'])).toBe(true);
    expect(auth.debeCambiarClave()).toBe(false);
    expect(sessionStorage.getItem('eduspace_token')).toBeNull();
  });

  it('antes de cada llamada pide a keycloak-js un token con al menos 30 s de vida', async () => {
    const { kc, http, ctrl, auth } = entorno();
    const respuesta = firstValueFrom(http.get('/api/v1/planificacion/anios'));
    await vi.waitFor(() => ctrl.expectOne('/api/v1/planificacion/anios'));
    expect(kc.actualizarToken).toHaveBeenCalledWith(30);
    expect(auth.getToken()).toBe(kc.token);
    void respuesta;
  });

  it('agrega el Bearer con el token vigente', async () => {
    const { kc, http, ctrl } = entorno();
    http.get('/api/v1/pagos/conceptos').subscribe();
    const req = await vi.waitFor(() => ctrl.expectOne('/api/v1/pagos/conceptos'));
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${kc.token}`);
    req.flush([]);
  });

  it('un 401 fuerza la renovación en Keycloak y repite la petición con el token nuevo', async () => {
    const { kc, http, ctrl } = entorno();
    let datos: unknown;
    http.get('/api/v1/auth/usuarios').subscribe(d => (datos = d));
    const primera = await vi.waitFor(() => ctrl.expectOne('/api/v1/auth/usuarios'));
    primera.flush({}, { status: 401, statusText: 'Unauthorized' });

    const reintento = await vi.waitFor(() => ctrl.expectOne('/api/v1/auth/usuarios'));
    expect(kc.actualizarToken).toHaveBeenCalledWith(-1);
    expect(reintento.request.headers.get('Authorization')).toBe(`Bearer ${kc.token}`);
    reintento.flush({ ok: true });
    expect(datos).toEqual({ ok: true });
  });

  it('cerrar sesión sale por Keycloak y no navega al login propio', () => {
    const { kc, auth } = entorno();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate');
    auth.logout();
    expect(kc.logout).toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('cambiar clave abre la cuenta de Keycloak', () => {
    const { kc, auth } = entorno();
    auth.abrirCuenta();
    expect(kc.cuenta).toHaveBeenCalled();
  });

  it('modoGuard: la pantalla de usuarios propia no existe con Keycloak', () => {
    entorno();
    const ruta = { data: { modo: 'eduspace' } } as unknown as ActivatedRouteSnapshot;
    const r = TestBed.runInInjectionContext(() => modoGuard(ruta, {} as never));
    expect(r instanceof UrlTree).toBe(true);
  });
});
