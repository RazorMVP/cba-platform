import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import Keycloak from 'keycloak-js';

/**
 * Attaches the Keycloak Bearer token and X-Tenant-ID header to every API call.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloak = inject(Keycloak);
  const token = keycloak.token;

  if (token && req.url.includes('/api/')) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
        'X-Tenant-ID': 'default',
      },
    });
    return next(cloned);
  }
  return next(req);
};
