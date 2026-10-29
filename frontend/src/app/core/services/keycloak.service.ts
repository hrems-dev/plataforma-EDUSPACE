import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KeycloakConfig } from '../config/app-config';

/** Envoltorio fino de keycloak-js: así el resto de la app (y las pruebas) no dependen de la librería. */
@Injectable({ providedIn: 'root' })
export class KeycloakService {
  private kc: Keycloak | null = null;
  private config: KeycloakConfig | null = null;

  /**
   * Con `obligatorio` (pantallas del portal): si no hay sesión redirige al login de Keycloak (código con PKCE).
   * Sin él (sitio público): comprueba en silencio (iframe) si ya hay sesión en Keycloak, sin redirigir ni mostrar nada;
   * así la página pública sabe que el usuario ya entró y el token existe desde el primer momento.
   */
  async iniciar(config: KeycloakConfig, obligatorio = true): Promise<boolean> {
    this.config = config;
    this.kc = new Keycloak({ url: config.url, realm: config.realm, clientId: config.clientId });
    const carga = obligatorio
      ? { onLoad: 'login-required' as const }
      : { onLoad: 'check-sso' as const, silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`, silentCheckSsoFallback: false };
    return this.kc.init({ ...carga, pkceMethod: 'S256', checkLoginIframe: false });
  }

  get autenticado(): boolean {
    return !!this.kc?.authenticated;
  }

  get token(): string | null {
    return this.kc?.token ?? null;
  }

  /** Renueva si al token le quedan menos de `minValidezSeg` segundos; -1 fuerza la renovación. */
  async actualizarToken(minValidezSeg = 30): Promise<boolean> {
    if (!this.kc) {
      return false;
    }
    return this.kc.updateToken(minValidezSeg);
  }

  login(redirectUri?: string): Promise<void> {
    return this.kc!.login({ redirectUri });
  }

  logout(): Promise<void> {
    return this.kc!.logout({ redirectUri: window.location.origin });
  }

  /** Consola de la cuenta del usuario (cambiar clave, sesiones, 2FA…). */
  cuenta(): Promise<void> {
    return this.kc!.accountManagement();
  }

  /** Consola de administración del realm (altas de usuarios y roles). */
  get consolaAdminUrl(): string {
    const c = this.config;
    return c ? `${c.url}/admin/${c.realm}/console/` : '';
  }
}
