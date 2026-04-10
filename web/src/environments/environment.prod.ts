// Build-time env var injection (Vercel sets these before `ng build`).
// Declare process so TypeScript is happy in the browser target —
// esbuild replaces process.env.* with literal strings at bundle time.
declare const process: { env: Record<string, string | undefined> };

export const environment = {
  production: true,
  authBypass: (typeof process !== 'undefined' && process.env['NG_APP_AUTH_BYPASS'] === 'true'),
  apiBaseUrl: (typeof process !== 'undefined' && process.env['NG_APP_API_URL'])
    ? process.env['NG_APP_API_URL']
    : '/api/v1',
  keycloak: {
    url: (typeof process !== 'undefined' && process.env['NG_APP_KEYCLOAK_URL'])
      ? process.env['NG_APP_KEYCLOAK_URL']
      : 'https://auth.cba.com',
    realm: (typeof process !== 'undefined' && process.env['NG_APP_KEYCLOAK_REALM'])
      ? process.env['NG_APP_KEYCLOAK_REALM']
      : 'cba',
    clientId: (typeof process !== 'undefined' && process.env['NG_APP_KEYCLOAK_CLIENT_ID'])
      ? process.env['NG_APP_KEYCLOAK_CLIENT_ID']
      : 'cba-web',
  },
};
