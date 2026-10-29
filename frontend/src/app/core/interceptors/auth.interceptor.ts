import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

/** Login y refresh se llaman sin token y su 401 significa "credenciales inválidas", no "sesión vencida". */
const esRutaPublica = (req: HttpRequest<unknown>): boolean =>
  /\/api\/v1\/auth\/(login|refresh)$/.test(req.url);

/** El Bearer solo viaja a nuestra API (ruta relativa /api/ o el mismo origen), nunca a un dominio ajeno. */
const esApiPropia = (url: string): boolean => {
  try {
    const u = new URL(url, window.location.origin);
    return u.origin === window.location.origin && u.pathname.startsWith('/api/');
  } catch {
    return false;
  }
};

let avisoRechazoMostrado = false;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const avisos = inject(NotificationService);
  const publica = esRutaPublica(req);

  // X-Trace-ID: el backend (CorrelationIdFilter) lo respeta, así una petición del navegador se sigue de punta a punta en los logs.
  const conToken = (r: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> => {
    if (!esApiPropia(r.url)) return r;
    const cabeceras: Record<string, string> = { 'X-Trace-ID': r.headers.get('X-Trace-ID') ?? crypto.randomUUID() };
    if (token && !publica) cabeceras['Authorization'] = `Bearer ${token}`;
    return r.clone({ setHeaders: cabeceras });
  };

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
            switchMap(() => next(conToken(req, auth.getToken()))),
            catchError((e: HttpErrorResponse) => {
              // Con token recién renovado el servidor lo sigue rechazando: la sesión es válida pero el backend no la acepta
              // (p. ej. frontend en modo Keycloak y servicios con otra fuente de claves). Se avisa una vez, no se deja la pantalla vacía.
              if (e.status === 401 && auth.esKeycloak && !avisoRechazoMostrado) {
                avisoRechazoMostrado = true;
                avisos.error('El servidor no aceptó tu sesión de Keycloak. Revisa que el backend esté configurado con Keycloak (JWK_SET_URI) o cambia el frontend a login propio.');
              }
              return throwError(() => e);
            })
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
