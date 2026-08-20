import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideKeycloak, withAutoRefreshToken, AutoRefreshTokenService, UserActivityService } from 'keycloak-angular';
import Keycloak from 'keycloak-js';

import { routes } from './app.routes';
import { environment } from '../environments/environment';
import { authInterceptor } from './core/auth/auth.interceptor';
import { DEMO_KEYCLOAK } from './core/auth/demo-keycloak';

const keycloakProviders = environment.authBypass
  ? [{ provide: Keycloak, useValue: DEMO_KEYCLOAK }]
  : [
      provideKeycloak({
        config: {
          url: environment.keycloak.url,
          realm: environment.keycloak.realm,
          clientId: environment.keycloak.clientId,
        },
        initOptions: {
          onLoad: 'login-required',
          checkLoginIframe: false,
        },
        features: [
          withAutoRefreshToken({
            onInactivityTimeout: 'logout',
            sessionTimeout: 60000,
          }),
        ],
        providers: [AutoRefreshTokenService, UserActivityService],
      }),
    ];

export const appConfig: ApplicationConfig = {
  providers: [
    // Angular 21 defaults to zoneless CD even with zone.js loaded; the components
    // in this app mutate plain properties inside RxJS subscribes (no signals), which
    // only re-render under zone-based change detection. Opt in explicitly.
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    provideStore({}),
    provideEffects([]),
    ...keycloakProviders,
  ],
};
