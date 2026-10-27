import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

/** Login y refresh se llaman sin token y su 401 significa "credenciales inválidas", no "sesión vencida". */
const esRutaPublica = (req: HttpRequest<unknown>): boolean =>
  /\/api\/v1\/auth\/(login|refresh)$/.test(req.url);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const avisos = inject(NotificationService);
  const publica = esRutaPublica(req);

  const conToken = (r: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> =>
    token && !publica ? r.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : r;

  // En modo Keycloak el token se renueva aquí antes de que venza; en modo propio devuelve el actual.
  return auth.tokenFresco().pipe(
    switchMap(token => next(conToken(req, token))),
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !publica) {
        if (auth.puedeRenovar()) {
          // Access token vencido: se renueva una vez y se repite la petición original. Solo si la RENOVACIÓN falla
          // la sesión terminó; si la repetición vuelve a dar 401, es el servicio quien rechaza el token (p. ej. auth-ms con
          // un token de Keycloak) y el error se muestra sin cerrar la sesión.
          return auth.renovar().pipe(
            catchError((e: unknown) => {
              avisos.warning('Tu sesión venció. Inicia sesión de nuevo.');
              auth.cerrarSesionLocal();
              return throwError(() => e);
            }),
            switchMap(() => next(conToken(req, auth.getToken())))
          );
        }
        auth.cerrarSesionLocal();
      } else if (error.status === 403) {
        avisos.error('No tienes permisos para realizar esta acción.');
      }
      return throwError(() => error);
    })
  );
};
