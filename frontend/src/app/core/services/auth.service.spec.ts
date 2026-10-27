import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { jwtFalso } from '../testing/jwt-falso';

function configurar(): { auth: AuthService; http: HttpTestingController } {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpTestingController) };
}

const respuesta = (roles: string[], debeCambiar = false) => ({
  access_token: jwtFalso(roles),
  refresh_token: 'refresh-1',
  token_type: 'Bearer',
  expires_in: 1800,
  debe_cambiar_clave: debeCambiar
});

describe('AuthService', () => {
  beforeEach(() => sessionStorage.clear());

  it('login guarda la sesión y lee usuario y roles del token', () => {
    const { auth, http } = configurar();
    auth.login({ username: 'admin', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(respuesta(['ADMIN', 'SECRETARIA']));

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.isAdmin()).toBe(true);
    expect(auth.currentUser()?.id).toBe(7);
    expect(auth.tieneAlgunRol(['CAJERO', 'SECRETARIA'])).toBe(true);
    expect(auth.tieneAlgunRol(['CAJERO'])).toBe(false);
    expect(sessionStorage.getItem('eduspace_refresh')).toBe('refresh-1');
  });

  it('recuerda que la clave es temporal', () => {
    const { auth, http } = configurar();
    auth.login({ username: 'u', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(respuesta(['SECRETARIA'], true));
    expect(auth.debeCambiarClave()).toBe(true);
  });

  it('varias renovaciones a la vez comparten una sola petición (el refresh token rota en cada uso)', () => {
    const { auth, http } = configurar();
    auth.login({ username: 'admin', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(respuesta(['ADMIN']));

    auth.renovar().subscribe();
    auth.renovar().subscribe();
    auth.renovar().subscribe();

    const peticiones = http.match('/api/v1/auth/refresh');
    expect(peticiones.length).toBe(1);
    expect(peticiones[0].request.body).toEqual({ refresh_token: 'refresh-1' });
    peticiones[0].flush({ ...respuesta(['ADMIN']), refresh_token: 'refresh-2' });
    expect(auth.getRefreshToken()).toBe('refresh-2');
  });

  it('restaura la sesión al recargar, incluso con el access token vencido si queda el refresh', () => {
    sessionStorage.setItem('eduspace_token', jwtFalso(['ADMIN'], -60));
    sessionStorage.setItem('eduspace_refresh', 'refresh-x');
    const { auth } = configurar();
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.getRefreshToken()).toBe('refresh-x');
  });

  it('cambiar la clave cierra la sesión local (el backend revoca las demás)', () => {
    const { auth, http } = configurar();
    auth.login({ username: 'admin', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(respuesta(['ADMIN']));

    auth.cambiarClave({ claveActual: 'a', claveNueva: 'bbbbbbbb' }).subscribe();
    http.expectOne('/api/v1/auth/cambiar-clave').flush(null, { status: 204, statusText: 'No Content' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('eduspace_token')).toBeNull();
  });
});
