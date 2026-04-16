export const environment = {
  production: false,
  authBypass: true,
  apiBaseUrl: 'http://localhost:8080/api/v1',
  cardServiceUrl: 'http://localhost:8081',
  keycloak: {
    url: 'http://localhost:8180',
    realm: 'cba',
    clientId: 'cba-web',
  },
};
