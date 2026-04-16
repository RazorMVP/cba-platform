import { CanActivateFn, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { createAuthGuard } from 'keycloak-angular';
import { environment } from '../../../environments/environment';

// In bypass mode (Vercel demo), skip Keycloak check entirely.
// In normal mode, redirect to Keycloak login if unauthenticated.
export const isAuthenticated: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  if (environment.authBypass) return true;
  return createAuthGuard(async () => true)(route, state);
};
