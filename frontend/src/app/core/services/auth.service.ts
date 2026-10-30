import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, from, map, of, shareReplay, tap, throwError } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import {
  CambiarClaveRequest,
  CurrentUser,
  JwtClaims,
  LoginRequest,
  TokenResponse
} from '../models/auth.model';
import { KeycloakService } from './keycloak.service';

const TOKEN_KEY = 'eduspace_token';
const REFRESH_KEY = 'eduspace_refresh';
const CAMBIAR_CLAVE_KEY = 'eduspace_cambiar_clave';
const API = '/api/v1/auth';
const FORM = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });

interface KeycloakTokens {
  access_token: string;
  refresh_token: string;
  token_type?: string;
  expires_in: number;
}

/**
 * Sesión del usuario. Dos emisores de token con el mismo contrato (JWT RS256, `usuarioId`, `realm_access.roles`):
 * - `eduspace`: login propio contra auth-ms (usuario y clave, refresh con rotación).
 * - `keycloak`: Keycloak autentica (código de autorización + PKCE) y keycloak-js renueva el token.
 * - `keycloak-form`: el formulario de EduSpace envía usuario y clave a Keycloak (credenciales directas), que valida y emite el token;
 *   no necesita Web Crypto, así que sirve por http desde otros equipos de la red.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly kc = inject(KeycloakService);

  private readonly cfg = inject(APP_CONFIG);
  readonly modo = this.cfg.authMode;
  /** Keycloak es quien emite el token (con redirección o con el formulario propio). */
  readonly esKeycloak = this.modo === 'keycloak' || this.modo === 'keycloak-form';
  /** Keycloak con redirección a su pantalla de login (PKCE). */
  readonly usaRedirect = this.modo === 'keycloak';
  private readonly conFormulario = this.modo === 'keycloak-form';

  private readonly tokenSignal = signal<string | null>(null);
  private readonly refreshSignal = signal<string | null>(null);
  private readonly currentUserSignal = signal<CurrentUser | null>(null);
  private readonly cambiarClaveSignal = signal(false);

  /** La renovación es una sola a la vez: el refresh token rota en cada uso y reutilizar uno viejo cierra todas las sesiones. */
  private renovacion$: Observable<unknown> | null = null;

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenSignal());
  readonly debeCambiarClave = this.cambiarClaveSignal.asReadonly();
  readonly roles = computed(() => this.currentUserSignal()?.roles ?? []);
  readonly isAdmin = computed(() => this.roles().includes('ADMIN'));

  constructor() {
    if (this.usaRedirect) {
      this.sincronizarKeycloak();
    } else {
      this.restaurarSesion();
    }
  }

  // --- login propio (modo eduspace) ---
  login(request: LoginRequest): Observable<TokenResponse> {
    if (this.conFormulario) {
      const cuerpo = new HttpParams().set('grant_type', 'password').set('client_id', this.clienteFormulario())
        .set('username', request.username).set('password', request.password);
      return this.http.post<KeycloakTokens>(this.urlKeycloak('token'), cuerpo.toString(), { headers: FORM }).pipe(
        map(r => this.aTokenResponse(r)),
        tap(res => this.guardarSesion(res))
      );
    }
    return this.http.post<TokenResponse>(`${API}/login`, request).pipe(tap(res => this.guardarSesion(res)));
  }

  cambiarClave(request: CambiarClaveRequest): Observable<void> {
    return this.http.post<void>(`${API}/cambiar-clave`, request).pipe(
      tap(() => {
        // Cambiar la clave cierra las demás sesiones y revoca el refresh token: se vuelve a iniciar sesión.
        this.limpiarSesion();
      })
    );
  }

  // --- común a los dos modos ---
  /** Hay forma de renovar el token sin pedir la clave otra vez. */
  puedeRenovar(): boolean {
    return this.usaRedirect ? this.kc.autenticado : !!this.refreshSignal();
  }

  /** Renueva el access token. Las llamadas simultáneas comparten una sola petición. */
  renovar(): Observable<unknown> {
    if (!this.puedeRenovar()) {
      return throwError(() => new Error('No hay sesión que renovar.'));
    }
    if (!this.renovacion$) {
      const refrescoKeycloak$ = (): Observable<unknown> => {
        const cuerpo = new HttpParams().set('grant_type', 'refresh_token').set('client_id', this.clienteFormulario()).set('refresh_token', this.refreshSignal() ?? '');
        return this.http.post<KeycloakTokens>(this.urlKeycloak('token'), cuerpo.toString(), { headers: FORM }).pipe(
          map(r => this.aTokenResponse(r)),
          tap(res => this.guardarSesion(res))
        );
      };
      const origen$: Observable<unknown> = this.usaRedirect
        ? from(this.kc.actualizarToken(-1)).pipe(tap(() => this.sincronizarKeycloak()))
        : this.conFormulario
          ? refrescoKeycloak$()
          : this.http.post<TokenResponse>(`${API}/refresh`, { refresh_token: this.refreshSignal() }).pipe(tap(res => this.guardarSesion(res)));
      this.renovacion$ = origen$.pipe(
        finalize(() => (this.renovacion$ = null)),
        shareReplay(1)
      );
    }
    return this.renovacion$;
  }

  /** Token listo para usar: en Keycloak se renueva antes de que venza (keycloak-js lo hace solo si queda poco). */
  tokenFresco(): Observable<string | null> {
    if (!this.usaRedirect) {
      return of(this.tokenSignal());
    }
    return from(this.kc.actualizarToken(30)).pipe(
      tap(() => this.sincronizarKeycloak()),
      map(() => this.kc.token),
      catchError(() => of(this.kc.token))
    );
  }

  logout(): void {
    if (this.usaRedirect) {
      void this.kc.logout();
      return;
    }
    const refresh = this.refreshSignal();
    if (refresh) {
      const cierre$ = this.conFormulario
        ? this.http.post<void>(this.urlKeycloak('logout'), new HttpParams().set('client_id', this.clienteFormulario()).set('refresh_token', refresh).toString(), { headers: FORM })
        : this.http.post<void>(`${API}/logout`, { refresh_token: refresh });
      cierre$.subscribe({ error: () => undefined });
    }
    this.cerrarSesionLocal();
  }

  /**
   * Borra la sesión del navegador y vuelve al login (p. ej. cuando el servidor ya no acepta el token porque venció o se reinició).
   * `motivo` hace que el login explique qué pasó, y `returnUrl` devuelve a la persona a la pantalla donde estaba.
   */
  cerrarSesionLocal(motivo?: 'vencida' | 'invalida'): void {
    if (this.usaRedirect) {
      void this.kc.logout();
      return;
    }
    const url = this.router.url;
    const hayRetorno = !!url && url !== '/' && !url.startsWith('/login') && !url.startsWith('/cambiar-clave');
    this.limpiarSesion();
    const queryParams: Record<string, string> = {};
    if (motivo) queryParams['motivo'] = motivo;
    if (hayRetorno && motivo) queryParams['returnUrl'] = url;
    this.router.navigate(['/login'], Object.keys(queryParams).length ? { queryParams } : undefined);
  }

  /** Abre la consola de la cuenta (Keycloak) donde el usuario cambia su clave y gestiona sus sesiones. */
  abrirCuenta(): void {
    if (this.conFormulario) {
      window.open(`${location.protocol}//${location.hostname}:18090/realms/${this.cfg.keycloak.realm}/account`, '_blank', 'noopener');
      return;
    }
    void this.kc.cuenta();
  }

  get consolaAdminUrl(): string {
    return this.conFormulario ? `${location.protocol}//${location.hostname}:18090/admin/${this.cfg.keycloak.realm}/console/` : this.kc.consolaAdminUrl;
  }

  getToken(): string | null {
    return this.tokenSignal();
  }

  getRefreshToken(): string | null {
    return this.refreshSignal();
  }

  tieneAlgunRol(roles: readonly string[]): boolean {
    const mios = this.roles();
    return roles.some(r => mios.includes(r));
  }

  // --- internos ---
  private clienteFormulario(): string {
    return this.cfg.keycloak.clientIdFormulario ?? 'eduspace-web-form';
  }

  private urlKeycloak(operacion: 'token' | 'logout'): string {
    const { url, realm } = this.cfg.keycloak;
    return `${url.replace(/\/$/, '')}/realms/${realm}/protocol/openid-connect/${operacion}`;
  }

  private aTokenResponse(r: KeycloakTokens): TokenResponse {
    return { access_token: r.access_token, refresh_token: r.refresh_token, token_type: r.token_type ?? 'Bearer', expires_in: r.expires_in, debe_cambiar_clave: false };
  }

  private sincronizarKeycloak(): void {
    const token = this.kc.token;
    this.tokenSignal.set(token);
    this.cambiarClaveSignal.set(false); // Keycloak resuelve las claves temporales con sus "acciones requeridas"
    this.currentUserSignal.set(token ? this.decodificar(token, true) : null);
  }

  private guardarSesion(res: TokenResponse): void {
    sessionStorage.setItem(TOKEN_KEY, res.access_token);
    sessionStorage.setItem(REFRESH_KEY, res.refresh_token);
    sessionStorage.setItem(CAMBIAR_CLAVE_KEY, String(!!res.debe_cambiar_clave));
    this.tokenSignal.set(res.access_token);
    this.refreshSignal.set(res.refresh_token);
    this.cambiarClaveSignal.set(!!res.debe_cambiar_clave);
    this.currentUserSignal.set(this.decodificar(res.access_token));
  }

  private limpiarSesion(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_KEY);
    sessionStorage.removeItem(CAMBIAR_CLAVE_KEY);
    this.tokenSignal.set(null);
    this.refreshSignal.set(null);
    this.cambiarClaveSignal.set(false);
    this.currentUserSignal.set(null);
  }

  private restaurarSesion(): void {
    const token = sessionStorage.getItem(TOKEN_KEY);
    const refresh = sessionStorage.getItem(REFRESH_KEY);
    if (!token || !refresh) {
      return;
    }
    // Un access token vencido no se descarta: si queda el refresh token, el interceptor lo renueva en la primera llamada.
    const usuario = this.decodificar(token, true);
    if (!usuario) {
      this.limpiarSesion();
      return;
    }
    this.tokenSignal.set(token);
    this.refreshSignal.set(refresh);
    this.cambiarClaveSignal.set(sessionStorage.getItem(CAMBIAR_CLAVE_KEY) === 'true');
    this.currentUserSignal.set(usuario);
  }

  private decodificar(token: string, ignorarVencimiento = false): CurrentUser | null {
    try {
      const partes = token.split('.');
      if (partes.length !== 3) {
        return null;
      }
      const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      const claims: JwtClaims = JSON.parse(json);
      if (!ignorarVencimiento && claims.exp && claims.exp * 1000 < Date.now()) {
        return null;
      }
      return {
        id: Number(claims.usuarioId ?? claims.sub),
        username: claims.preferred_username ?? '',
        email: claims.email ?? '',
        roles: claims.realm_access?.roles ?? []
      };
    } catch {
      return null;
    }
  }
}
