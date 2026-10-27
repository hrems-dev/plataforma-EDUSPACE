import { InjectionToken } from '@angular/core';

export type ModoAuth = 'eduspace' | 'keycloak';

export interface KeycloakConfig {
  url: string;
  realm: string;
  clientId: string;
}

export interface AppConfig {
  /** `eduspace`: login propio contra auth-ms. `keycloak`: código de autorización con PKCE contra Keycloak. */
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
    return {
      authMode: cfg.authMode === 'keycloak' ? 'keycloak' : 'eduspace',
      keycloak: { ...CONFIG_POR_DEFECTO.keycloak, ...(cfg.keycloak ?? {}) }
    };
  } catch {
    return CONFIG_POR_DEFECTO;
  }
}
