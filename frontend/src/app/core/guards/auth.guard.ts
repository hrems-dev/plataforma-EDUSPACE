import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { KeycloakService } from '../services/keycloak.service';

/** Exige sesión. Si la clave es temporal, solo deja entrar a /cambiar-clave. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    // Con Keycloak se entra desde el sitio público (sin sesión): se manda al login de Keycloak y se vuelve a esta pantalla.
    if (auth.esKeycloak) {
      void inject(KeycloakService).login(window.location.origin + state.url);
      return false;
    }
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  if (auth.debeCambiarClave() && !state.url.startsWith('/cambiar-clave')) {
    return router.createUrlTree(['/cambiar-clave']);
  }
  return true;
};

/** Solo para quien no tiene sesión (login). En modo Keycloak nunca se llega sin sesión, así que /login vuelve al inicio. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/portal']) : true;
};
