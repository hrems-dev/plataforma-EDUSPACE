import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

/** Uso: `data: { roles: ['ADMIN', 'SECRETARIA'] }`. El backend vuelve a validar el rol en cada llamada; esto solo evita pantallas inútiles. */
export const roleGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const avisos = inject(NotificationService);

  const requeridos = (route.data?.['roles'] as string[] | undefined) ?? [];
  if (requeridos.length === 0 || auth.tieneAlgunRol(requeridos)) {
    return true;
  }
  avisos.error('No tienes acceso a esa sección.');
  return router.createUrlTree(['/portal']);
};
