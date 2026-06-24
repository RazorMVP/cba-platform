import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { isAuthenticated } from './auth.guard';
import { environment } from '../../../environments/environment';

// Stub keycloak-angular so the non-bypass branch is observable without a real
// Keycloak instance: createAuthGuard returns a guard that reports it was called.
vi.mock('keycloak-angular', () => ({
  createAuthGuard: () => () => 'KEYCLOAK_GUARD_INVOKED',
}));

describe('isAuthenticated guard', () => {
  const originalBypass = environment.authBypass;
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  afterEach(() => {
    environment.authBypass = originalBypass;
  });

  it('returns true immediately in bypass mode (no Keycloak check)', () => {
    environment.authBypass = true;
    expect(isAuthenticated(route, state)).toBe(true);
  });

  it('delegates to the Keycloak auth guard when bypass is off', () => {
    environment.authBypass = false;
    expect(isAuthenticated(route, state)).toBe('KEYCLOAK_GUARD_INVOKED');
  });
});
