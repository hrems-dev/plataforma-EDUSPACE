import { provideAppInitializer, inject } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { APP_CONFIG, cargarConfig } from './app/core/config/app-config';
import { esRutaPublica } from './app/core/utils/publicas';
import { KeycloakService } from './app/core/services/keycloak.service';

// La configuración se lee en tiempo de ejecución (public/config.json). En modo Keycloak, la app no arranca hasta
// que hay sesión: keycloak-js redirige al login y vuelve con el código.
cargarConfig().then(config =>
  bootstrapApplication(App, {
    ...appConfig,
    providers: [
      ...appConfig.providers,
      { provide: APP_CONFIG, useValue: config },
      provideAppInitializer(async () => {
        if (config.authMode === 'keycloak') {
          // El sitio público se ve sin sesión; el portal obliga a iniciarla (también al volver del login con ?code en el hash).
          const volviendoDelLogin = location.hash.includes('code=') || location.hash.includes('state=');
          await inject(KeycloakService).iniciar(config.keycloak, volviendoDelLogin || !esRutaPublica(location.pathname));
        }
      })
    ]
  }).catch(err => console.error(err))
);
