import Keycloak from 'keycloak-js';

/**
 * Minimal Keycloak mock for demo / auth-bypass mode.
 * Used when NG_APP_AUTH_BYPASS=true so the Vercel preview loads without
 * a real Keycloak instance. Never used in production with real auth.
 */
export const DEMO_KEYCLOAK = {
  token: 'demo-token',
  authenticated: true,
  tokenParsed: {
    sub: 'demo-user-id',
    preferred_username: 'demo.admin',
    name: 'Demo Admin',
    email: 'demo@cba.com',
    realm_access: { roles: ['ADMIN', 'TELLER'] },
  },
  login: () => Promise.resolve(),
  logout: () => { window.location.href = '/'; },
  updateToken: () => Promise.resolve(false),
  isTokenExpired: () => false,
  hasRealmRole: (_role: string) => true,
  hasResourceRole: (_role: string) => true,
  createLoginUrl: () => '#',
  createLogoutUrl: () => '/',
} as unknown as Keycloak;
