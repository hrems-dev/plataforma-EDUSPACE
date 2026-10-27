import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';
import { jwtFalso } from '../testing/jwt-falso';

const estado = (url: string) => ({ url }) as RouterStateSnapshot;
const ruta = (roles?: string[]) => ({ data: roles ? { roles } : {} }) as unknown as ActivatedRouteSnapshot;

function iniciarSesion(roles: string[], debeCambiar = false): void {
  sessionStorage.setItem('eduspace_token', jwtFalso(roles));
  sessionStorage.setItem('eduspace_refresh', 'r');
  sessionStorage.setItem('eduspace_cambiar_clave', String(debeCambiar));
}

function entorno(): Router {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  TestBed.inject(AuthService);
  return TestBed.inject(Router);
}

const vaA = (r: unknown, destino: string) => (r as UrlTree).toString().startsWith(destino);

describe('guards', () => {
  beforeEach(() => sessionStorage.clear());

  it('authGuard sin sesión manda al login recordando la URL', () => {
    entorno();
    const r = TestBed.runInInjectionContext(() => authGuard(ruta(), estado('/usuarios')));
    expect(vaA(r, '/login?returnUrl=%2Fusuarios')).toBe(true);
  });

  it('authGuard con sesión deja pasar', () => {
    iniciarSesion(['ADMIN']);
    entorno();
    expect(TestBed.runInInjectionContext(() => authGuard(ruta(), estado('/usuarios')))).toBe(true);
  });

  it('con clave temporal solo deja entrar a /cambiar-clave', () => {
    iniciarSesion(['ADMIN'], true);
    entorno();
    expect(vaA(TestBed.runInInjectionContext(() => authGuard(ruta(), estado('/usuarios'))), '/cambiar-clave')).toBe(true);
    expect(TestBed.runInInjectionContext(() => authGuard(ruta(), estado('/cambiar-clave')))).toBe(true);
  });

  it('guestGuard saca del login a quien ya inició sesión', () => {
    iniciarSesion(['ADMIN']);
    entorno();
    expect(vaA(TestBed.runInInjectionContext(() => guestGuard(ruta(), estado('/login'))), '/')).toBe(true);
  });

  it('roleGuard deja pasar al rol correcto y bloquea al que no lo tiene', () => {
    iniciarSesion(['SECRETARIA']);
    entorno();
    expect(TestBed.runInInjectionContext(() => roleGuard(ruta(['ADMIN', 'SECRETARIA']), estado('/x')))).toBe(true);
    expect(vaA(TestBed.runInInjectionContext(() => roleGuard(ruta(['ADMIN']), estado('/usuarios'))), '/')).toBe(true);
  });
});
