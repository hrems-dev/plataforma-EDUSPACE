import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { jwtFalso } from '../testing/jwt-falso';

function iniciar(): { http: HttpClient; ctrl: HttpTestingController; auth: AuthService; router: Router } {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(), provideRouter([])]
  });
  const auth = TestBed.inject(AuthService);
  const ctrl = TestBed.inject(HttpTestingController);
  // Sesión iniciada con el interceptor ya activo: el login no lleva Authorization.
  auth.login({ username: 'admin', password: 'x' }).subscribe();
  ctrl.expectOne('/api/v1/auth/login').flush({
    access_token: jwtFalso(['ADMIN']), refresh_token: 'r1', token_type: 'Bearer', expires_in: 1800, debe_cambiar_clave: false
  });
  return { http: TestBed.inject(HttpClient), ctrl, auth, router: TestBed.inject(Router) };
}

describe('authInterceptor', () => {
  beforeEach(() => sessionStorage.clear());

  it('agrega el Bearer a las llamadas a la API', () => {
    const { http, ctrl, auth } = iniciar();
    http.get('/api/v1/planificacion/anios').subscribe();
    const req = ctrl.expectOne('/api/v1/planificacion/anios');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${auth.getToken()}`);
    req.flush([]);
  });

  it('cada llamada a la API lleva un X-Trace-ID (y se conserva el que ya traiga)', () => {
    const { http, ctrl } = iniciar();
    http.get('/api/v1/a').subscribe();
    const id = ctrl.expectOne('/api/v1/a').request.headers.get('X-Trace-ID');
    expect(id).toMatch(/^[0-9a-f-]{36}$/);
    http.get('/api/v1/b', { headers: { 'X-Trace-ID': 'mio-123' } }).subscribe();
    expect(ctrl.expectOne('/api/v1/b').request.headers.get('X-Trace-ID')).toBe('mio-123');
  });

  it('no manda el Bearer a un dominio ajeno ni a rutas que no son de la API', () => {
    const { http, ctrl } = iniciar();
    http.get('https://otro-sitio.com/api/v1/datos').subscribe();
    expect(ctrl.expectOne('https://otro-sitio.com/api/v1/datos').request.headers.has('Authorization')).toBe(false);
    http.get('/config.json').subscribe();
    expect(ctrl.expectOne('/config.json').request.headers.has('Authorization')).toBe(false);
  });

  it('login y refresh no llevan Authorization', () => {
    const { http, ctrl } = iniciar();
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    const req = ctrl.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
  });

  it('un 401 de login es "credenciales inválidas": no intenta renovar ni cierra la sesión', () => {
    const { http, ctrl, auth } = iniciar();
    const antes = auth.getToken();
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    ctrl.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectNone('/api/v1/auth/refresh');
    expect(auth.getToken()).toBe(antes);
  });

  it('un 401 renueva el token y repite la petición original con el token nuevo', () => {
    const { http, ctrl, auth } = iniciar();
    let datos: unknown;
    http.get('/api/v1/auth/usuarios').subscribe(d => (datos = d));

    ctrl.expectOne('/api/v1/auth/usuarios').flush({}, { status: 401, statusText: 'Unauthorized' });

    const nuevo = jwtFalso(['ADMIN'], 7200);
    const refresh = ctrl.expectOne('/api/v1/auth/refresh');
    expect(refresh.request.body).toEqual({ refresh_token: 'r1' });
    refresh.flush({ access_token: nuevo, refresh_token: 'r2', token_type: 'Bearer', expires_in: 1800, debe_cambiar_clave: false });

    const reintento = ctrl.expectOne('/api/v1/auth/usuarios');
    expect(reintento.request.headers.get('Authorization')).toBe(`Bearer ${nuevo}`);
    reintento.flush({ ok: true });
    expect(datos).toEqual({ ok: true });
    expect(auth.getRefreshToken()).toBe('r2');
  });

  it('dos 401 simultáneos provocan una sola renovación', () => {
    const { http, ctrl } = iniciar();
    http.get('/api/v1/a').subscribe();
    http.get('/api/v1/b').subscribe();
    ctrl.expectOne('/api/v1/a').flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne('/api/v1/b').flush({}, { status: 401, statusText: 'Unauthorized' });

    const renovaciones = ctrl.match('/api/v1/auth/refresh');
    expect(renovaciones.length).toBe(1);
    renovaciones[0].flush({ access_token: jwtFalso(['ADMIN']), refresh_token: 'r2', token_type: 'Bearer', expires_in: 1800, debe_cambiar_clave: false });
    ctrl.expectOne('/api/v1/a').flush({});
    ctrl.expectOne('/api/v1/b').flush({});
  });

  it('si la renovación falla, avisa y vuelve al login', () => {
    const { http, ctrl, auth, router } = iniciar();
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    http.get('/api/v1/a').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/v1/a').flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navegar).toHaveBeenCalledWith(['/login']);
    expect(TestBed.inject(NotificationService).avisos().some(a => a.tipo === 'warning')).toBe(true);
  });

  it('un 403 avisa que no hay permisos y no cierra la sesión', () => {
    const { http, ctrl, auth } = iniciar();
    http.get('/api/v1/pagos/conceptos').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/v1/pagos/conceptos').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.isAuthenticated()).toBe(true);
    expect(TestBed.inject(NotificationService).avisos().some(a => a.tipo === 'error')).toBe(true);
  });
});

describe('authInterceptor: servicio que rechaza el token', () => {
  beforeEach(() => sessionStorage.clear());

  it('si tras renovar bien la repetición sigue dando 401, NO cierra la sesión (es el servicio quien rechaza, no un vencimiento)', () => {
    const { http, ctrl, auth } = iniciar();
    http.get('/api/v1/auth/usuarios').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/v1/auth/usuarios').flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne('/api/v1/auth/refresh').flush({ access_token: jwtFalso(['ADMIN']), refresh_token: 'r2', token_type: 'Bearer', expires_in: 1800, debe_cambiar_clave: false });
    ctrl.expectOne('/api/v1/auth/usuarios').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(true);
  });
});
