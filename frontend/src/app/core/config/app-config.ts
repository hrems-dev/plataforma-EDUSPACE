import { InjectionToken } from '@angular/core';

/** `eduspace`: login propio (auth-ms). `keycloak`: redirección a Keycloak con PKCE. `keycloak-form`: formulario de EduSpace validado por Keycloak. */
export type ModoAuth = 'eduspace' | 'keycloak' | 'keycloak-form';

export interface KeycloakConfig {
  url: string;
  realm: string;
  clientId: string;
  /** Cliente de Keycloak para `keycloak-form` (credenciales directas). */
  clientIdFormulario?: string;
}

export interface AppConfig {
  /**
   * `eduspace`: login propio contra auth-ms.
   * `keycloak`: código de autorización con PKCE (necesita https o localhost: usa Web Crypto).
   * `keycloak-form`: el formulario de EduSpace envía usuario y clave a Keycloak, que los valida y emite el token; funciona por http en la
   * red local. `keycloak.url` puede ser una ruta del mismo origen (p. ej. `/kc`, proxy de DEV hacia Keycloak).
   */
  authMode: ModoAuth;
  keycloak: KeycloakConfig;
}

export const CONFIG_POR_DEFECTO: AppConfig = {
  authMode: 'eduspace',
  keycloak: { url: 'http://localhost:18090', realm: 'eduspace', clientId: 'eduspace-web' }
};

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG', {
  providedIn: 'root',
  factory: () => CONFIG_POR_DEFECTO
});

/** Lee `config.json` (junto a index.html) para poder cambiar de emisor sin recompilar. Si falta, usa el login propio. */
export async function cargarConfig(): Promise<AppConfig> {
  try {
    const res = await fetch('config.json', { cache: 'no-store' });
    if (!res.ok) {
      return CONFIG_POR_DEFECTO;
    }
    const cfg = (await res.json()) as Partial<AppConfig>;
    const keycloak = { ...CONFIG_POR_DEFECTO.keycloak, ...(cfg.keycloak ?? {}) };
    // `auto`: Keycloak corre en el mismo equipo que sirve la web, en el puerto 18090 (así sirve desde cualquier equipo de la red).
    if (keycloak.url === 'auto') keycloak.url = `${location.protocol}//${location.hostname}:18090`;
    return {
      authMode: cfg.authMode === 'keycloak' || cfg.authMode === 'keycloak-form' ? cfg.authMode : 'eduspace',
      keycloak
    };
  } catch {
    return CONFIG_POR_DEFECTO;
  }
}
