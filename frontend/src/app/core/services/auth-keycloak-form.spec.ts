import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from './auth.service';
import { jwtFalso } from '../testing/jwt-falso';

function configurar(): { auth: AuthService; http: HttpTestingController } {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: APP_CONFIG, useValue: { authMode: 'keycloak-form', keycloak: { url: '/kc', realm: 'eduspace', clientId: 'eduspace-web' } } }
    ]
  });
  return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpTestingController) };
}

const TOKEN = '/kc/realms/eduspace/protocol/openid-connect/token';
const tokens = (roles: string[]) => ({ access_token: jwtFalso(roles), refresh_token: 'kc-refresh', token_type: 'Bearer', expires_in: 300 });

describe('AuthService · keycloak-form (formulario de EduSpace validado por Keycloak)', () => {
  beforeEach(() => sessionStorage.clear());

  it('Keycloak emite el token, pero no hay redirección ni Web Crypto: usa credenciales directas', () => {
    const { auth, http } = configurar();
    expect(auth.esKeycloak).toBe(true);
    expect(auth.usaRedirect).toBe(false);

    auth.login({ username: 'admin', password: 'admin123' }).subscribe();
    const req = http.expectOne(TOKEN);
    expect(req.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    const cuerpo = new URLSearchParams(req.request.body as string);
    expect(cuerpo.get('grant_type')).toBe('password');
    expect(cuerpo.get('client_id')).toBe('eduspace-web-form');
    expect(cuerpo.get('username')).toBe('admin');
    req.flush(tokens(['ADMIN']));

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.isAdmin()).toBe(true);
    expect(auth.debeCambiarClave()).toBe(false);
  });

  it('renueva con el refresh token de Keycloak y cierra sesión contra Keycloak', () => {
    const { auth, http } = configurar();
    auth.login({ username: 'u', password: 'p' }).subscribe();
    http.expectOne(TOKEN).flush(tokens(['DOCENTE']));

    auth.renovar().subscribe();
    const ref = http.expectOne(TOKEN);
    const c = new URLSearchParams(ref.request.body as string);
    expect(c.get('grant_type')).toBe('refresh_token');
    expect(c.get('refresh_token')).toBe('kc-refresh');
    ref.flush({ ...tokens(['DOCENTE']), refresh_token: 'kc-refresh-2' });
    expect(auth.getRefreshToken()).toBe('kc-refresh-2');

    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.logout();
    http.expectOne('/kc/realms/eduspace/protocol/openid-connect/logout').flush(null);
    expect(auth.isAuthenticated()).toBe(false);
  });
});
