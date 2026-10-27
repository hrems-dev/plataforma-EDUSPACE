import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ModoAuth } from '../config/app-config';
import { AuthService } from '../services/auth.service';

/** Pantallas que solo existen en un modo de autenticación. Uso: `data: { modo: 'eduspace' }`. */
export const modoGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const requerido = route.data?.['modo'] as ModoAuth | undefined;
  return !requerido || requerido === auth.modo ? true : inject(Router).createUrlTree(['/portal']);
};
