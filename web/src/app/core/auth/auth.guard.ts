import { CanActivateFn } from '@angular/router';
import { createAuthGuard } from 'keycloak-angular';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

// Default guard — redirects to Keycloak login if unauthenticated
export const isAuthenticated: CanActivateFn = createAuthGuard(
  async (_route: ActivatedRouteSnapshot, _state: RouterStateSnapshot) => true
);
