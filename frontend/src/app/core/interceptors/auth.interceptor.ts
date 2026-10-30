import { HttpErrorResponse, HttpEventType, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, tap, throwError } from 'rxjs';
import { EstadoServicioService } from '../services/estado-servicio.service';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { uuid } from '../utils/uuid';

/**
 * Login y refresh se llaman sin token y su 401 significa "credenciales inválidas", no "sesión vencida".
 * La matrícula en línea del sitio público tampoco lleva token: un token vencido de otra sesión haría que el Gateway la rechace.
 */
const esRutaPublica = (req: HttpRequest<unknown>): boolean =>
  /\/api\/v1\/auth\/(login|refresh)$/.test(req.url) || /\/api\/v1\/publico\//.test(req.url) ||
  /\/realms\/[^/]+\/protocol\/openid-connect\//.test(req.url); // token/logout de Keycloak (modo keycloak-form)

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
  const estado = inject(EstadoServicioService);
  const publica = esRutaPublica(req);

  // X-Trace-ID: el backend (CorrelationIdFilter) lo respeta, así una petición del navegador se sigue de punta a punta en los logs.
  const conToken = (r: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> => {
    if (!esApiPropia(r.url)) return r;
    const cabeceras: Record<string, string> = { 'X-Trace-ID': r.headers.get('X-Trace-ID') ?? uuid() };
    if (token && !publica) cabeceras['Authorization'] = `Bearer ${token}`;
    return r.clone({ setHeaders: cabeceras });
  };

  // En modo Keycloak el token se renueva aquí antes de que venza; en modo propio devuelve el actual.
  return auth.tokenFresco().pipe(
    switchMap(token => next(conToken(req, token))),
    // Una respuesta buena de nuestra API apaga el aviso de «servicio sin conexión».
    tap({ next: ev => { if (ev.type === HttpEventType.Response && esApiPropia(req.url)) estado.marcarOk(req.url); } }),
    catchError((error: HttpErrorResponse) => {
      // Sin respuesta (0), Gateway sin el servicio (502/503) o tiempo agotado (504): el servicio se está reiniciando o cayó.
      // Mientras Eureka aún anuncia la instancia caída, el Gateway responde un 500 "crudo" (sin el `mensaje` que sí traen
      // los errores de nuestros servicios): también cuenta como servicio caído.
      const crudo500 = error.status === 500 && typeof error.error?.mensaje !== 'string';
      if (esApiPropia(req.url) && ([0, 502, 503, 504].includes(error.status) || crudo500)) estado.marcarCaido(req.url);
      if (error.status === 401 && !publica) {
        if (auth.puedeRenovar()) {
          // Access token vencido: se renueva una vez y se repite la petición original. Solo si la RENOVACIÓN falla
          // la sesión terminó; si la repetición vuelve a dar 401, es el servicio quien rechaza el token (p. ej. auth-ms con
          // un token de Keycloak) y el error se muestra sin cerrar la sesión.
          return auth.renovar().pipe(
            catchError((e: unknown) => {
              avisos.warning('Tu sesión venció. Inicia sesión de nuevo.');
              auth.cerrarSesionLocal('vencida');
              return throwError(() => e);
            }),
            switchMap(() => next(conToken(req, auth.getToken()))),
            catchError((e: HttpErrorResponse) => {
              // Con un token recién renovado el servidor lo sigue rechazando: la sesión ya no sirve (p. ej. el servicio se reinició
              // y cambió sus claves). Con el login propio se vuelve a iniciar sesión; con Keycloak se avisa una vez.
              if (e.status === 401) {
                if (!auth.usaRedirect) {
                  avisos.warning('El sistema no reconoce tu sesión (puede que se haya reiniciado). Inicia sesión de nuevo.');
                  auth.cerrarSesionLocal('invalida');
                } else if (!avisoRechazoMostrado) {
                  avisoRechazoMostrado = true;
                  avisos.error('El servidor no aceptó tu sesión de Keycloak. Revisa que el backend esté configurado con Keycloak (JWK_SET_URI) o cambia el frontend a login propio.');
                }
              }
              return throwError(() => e);
            })
          );
        }
        auth.cerrarSesionLocal('vencida');
      } else if (error.status === 403) {
        avisos.error('No tienes permisos para realizar esta acción.');
      }
      return throwError(() => error);
    })
  );
};
