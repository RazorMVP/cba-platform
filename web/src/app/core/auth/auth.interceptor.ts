import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../../environments/environment';

/**
 * Attaches the Keycloak Bearer token and X-Tenant-ID header to every API call.
 * In bypass/demo mode no token is available so the header is omitted.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (environment.authBypass) return next(req);

  const keycloak = inject(Keycloak);
  const token = keycloak.token;

  if (token && req.url.includes('/api/')) {
    return next(req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
        'X-Tenant-ID': 'default',
      },
    }));
  }
  return next(req);
};
