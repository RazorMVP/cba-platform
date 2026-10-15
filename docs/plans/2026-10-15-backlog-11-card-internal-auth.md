> **Status:** merged — PR #153, merge commit `d866430`. Approved 2026-10-15.  
> Recovered verbatim from the session transcript (the approved `ExitPlanMode` plan); the body below is unedited.

# Backlog 11 — card-service `/api/v1/internal/**`: authenticate fep-service, close the public route

## Context
card-service's `internalChain` (`SecurityConfig` `@Order(1)`, `/api/v1/internal/**`) is `permitAll`, justified by the comment "network isolation is the guard". That isolation doesn't exist:
- the k8s ingress routes `/` (Prefix) on `card-api.cba.example.com` to card-service;
- there is no NetworkPolicy anywhere.

So these endpoints are reachable from the internet with no credential:
- **`POST /detokenize`** returns the clear PAN for a DPAN.
- **`POST /authorize`** runs the full auth decision and writes the auth log.
- **`/advise`** and **`/reverse`**.
- **`GET /cards/{cardId}/authorizations`**: any card's history. It is dead; nobody calls it.

The only caller is fep-service's `com.cba.fep.auth.CardServiceClient`. It uses a bare `new RestTemplate()` (`FepConfig:25`), sends only `Content-Type`, and has no timeouts (`connect-timeout-ms`/`read-timeout-ms` are in yml but never read).

The same client also calls `GET /api/v1/bins/all` and `/{bin}/scheme`. Those sit on card-service's JWT chain, so without a token they get 401 and fep's BIN cache silently stays empty outside dev bypass.

**Constraint:** fep-service is on Spring Boot **3.2.5 / Spring Security 6.2.4**. `OAuth2ClientHttpRequestInterceptor` and the RestClient token client (used by the backend in cont. 33) are 6.4+, so they're not available here. fep-service has no Spring Security on its classpath, and no CI workflow runs its tests.

Branch `fix/card-service-internal-auth` off `main` (`a894869`), one PR. Merge only on the user's word with CI green.

## Decisions (recommended defaults; the user may change them)
- **D1. Token in fep-service: a small hand-written client-credentials provider, no new dependencies.** It POSTs `grant_type=client_credentials` with HTTP Basic, caches the token until `expires_in − 30 s`, and is thread-safe. Adding `spring-security-oauth2-client` 6.2 would pull in spring-security-web and nimbus and risk Boot's default web security locking fep's actuator probes. Upgrading fep to Boot 3.5 is a separate task.
- **D2. Least privilege:** a new realm role **`FEP_SERVICE`**, held only by the new client `cba-fep-service`'s service account. The internal chain requires `hasRole('FEP_SERVICE')`; ADMIN, TELLER, CUSTOMER and CARD_READER get 403.
- **D3. Dev bypass also covers the internal chain** (when `app.auth-bypass=true`, compose and local only), with `ROLE_FEP_SERVICE` added to the bypass authorities. This keeps the terminal-simulator demo working without Keycloak. A real token still wins when sent, and bypass is off in k8s and tests.
- **D4. Defence in depth at the edge:**
  - the public ingress sends `/api/v1/internal` and `/actuator` to a **blackhole Service** (no endpoints, so 503); this is controller-agnostic and needs no nginx snippets;
  - a **NetworkPolicy** lets only fep-service, backend and ingress-controller pods reach card-service:8081.
- **D5.** Add a minimal **`fep-service-ci.yml`** (unit tests plus a gate job, like the other modules), since the code that now carries a credential has no CI at all.

## 1. card-service
- `SecurityConfig.internalChain`:
  - `.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))`;
  - `authorizeHttpRequests(a -> a.anyRequest().hasRole("FEP_SERVICE"))`;
  - plus the dev-bypass filter (null-guarded, as in the other chains);
  - stateless, CSRF off, no CORS;
  - fix the class and chain comments.
- `DevAuthBypassFilter`: add `ROLE_FEP_SERVICE` to `DEV_AUTHORITIES`.
- `CardAuthorizationController`: delete the dead `GET /api/v1/internal/cards/{cardId}/authorizations`. The admin and backend paths `/card-api/v1/...` and `/api/v1/cards/{id}/authorizations` stay. Update the Javadoc.
- `publicChain`: `permitAll` `/actuator/health/**`. The k8s probes hit `/actuator/health/readiness|liveness`, and today only `/actuator/health` exactly is permitted, so probes would get 401 in k8s.
- Regenerate the OpenAPI snapshot.

## 2. fep-service
- **New `com.cba.fep.config.CardServiceTokenProvider`:**
  - built from `client-id`, `client-secret`, `token-uri`, its own `RestTemplate` with timeouts, and a `Clock`;
  - `String token()` is synchronized: reuse until `expiresAt − 30 s`, otherwise POST the form;
  - an error throws `ResourceAccessException` (a `RestClientException`), so `CardServiceClient`'s existing handlers degrade: authorize → RC96, reverse → "96", detokenize → DPAN unchanged, BIN → UNKNOWN or empty.
  - The token value is never logged.
- `FepConfig.restTemplate()`:
  - apply the existing `fep.card-service.connect-timeout-ms` and `read-timeout-ms`;
  - if `fep.card-service.auth.client-secret` is set, add an interceptor that sets `Authorization: Bearer` from the provider;
  - if it's blank, log a WARN and send no token (dev/bypass; `FepContextLoadTest` keeps booting).
  - The `CardServiceClient` constructor is unchanged, so `SchemeAdapterFactoryTest` keeps working.
- `application.yml`: `fep.card-service.auth.{client-id: ${FEP_CARD_SERVICE_CLIENT_ID:cba-fep-service}, client-secret: ${CBA_FEP_SERVICE_SECRET:}, token-uri: ${FEP_CARD_SERVICE_TOKEN_URI:http://localhost:8180/realms/cba/protocol/openid-connect/token}}`.

## 3. Keycloak realm (`cba-realm.json` + `k8s/keycloak/keycloak-realm-configmap.yaml`)
- Role `FEP_SERVICE` ("service-to-service only; never assign to people").
- Client `cba-fep-service`, the same shape as `cba-backend-service`: service accounts only, `"secret": "${CBA_FEP_SERVICE_SECRET}"`, `defaultClientScopes: ["roles"]`.
- Service-account user with `["FEP_SERVICE"]`.

## 4. Infrastructure
- `docker-compose.yml`:
  - Keycloak and fep-service get `CBA_FEP_SERVICE_SECRET: ${CBA_FEP_SERVICE_SECRET:-local-dev-only-change-me}`;
  - fep-service also gets `FEP_CARD_SERVICE_TOKEN_URI: http://keycloak:8180/realms/cba/protocol/openid-connect/token`.
- k8s:
  - `platform-secrets.yaml`: `CBA_FEP_SERVICE_SECRET` in `keycloak-secret` and `fep-service-secret`;
  - Keycloak and fep deployments read it as env;
  - the fep configmap gets the token URI.
- `k8s/card-service/`:
  - `card-service-blackhole-service.yaml`: a ClusterIP Service with no selector, so no endpoints.
  - The ingress gets Prefix paths `/api/v1/internal` and `/actuator` pointing at the blackhole (longest prefix wins over `/`).
  - `card-service-networkpolicy.yaml`: podSelector `app: card-service`; ingress on 8081 from `app: fep-service`, `app: backend`, and namespace `ingress-nginx`. The ingress-controller namespace is an assumption, commented, and listed in the runbook.
  - Placed in `k8s/card-service/` so the deploy job (`kubectl apply -f infrastructure/k8s/card-service/`) applies them.
- `.env.example`: the new variable.

## 5. Tests
- **card-service IT `InternalChainIntegrationTest`**, real HTTP with the `ROLES_TOKEN` decoder:
  - every internal endpoint with no token → 401;
  - ADMIN, TELLER, CARD_READER and CUSTOMER → 403;
  - FEP_SERVICE → reaches the handler (`/detokenize` with an unknown DPAN → 200 echo; `/reverse` with an unknown card → 200; `/authorize` with the minimal body from `FepAuthorizeContractTest` → 200 with a responseCode);
  - the dead history path → 404 or 401;
  - `/actuator/health/readiness` and `/liveness` → 200 without a token.
- **fep-service unit tests** (MockRestServiceServer from `spring-boot-starter-test`):
  - the provider sends Basic + form, and the token is cached across calls;
  - after expiry it fetches again (fixed `Clock`);
  - a 401 from the token endpoint → `ResourceAccessException`;
  - the interceptor adds Bearer;
  - a blank secret → no interceptor.
  - `FepContextLoadTest` still boots.
- **Backend `KeycloakRealmServiceClientTest`:** parameterise over (`cba-backend-service`, `CARD_READER`, `CBA_BACKEND_SERVICE_SECRET`) and (`cba-fep-service`, `FEP_SERVICE`, `CBA_FEP_SERVICE_SECRET`), for both realm copies.
- **Real Keycloak 23 check** (throwaway container, as in cont. 33): the fep token has `realm_access.roles == ["FEP_SERVICE"]`; a wrong secret → 401.
- **k8s manifests:** `yaml.safe_load_all`, and `kubectl apply --dry-run=client` if kubectl is available.

## 6. CI (D5)
- `.github/workflows/fep-service-ci.yml`, created with a Bash heredoc (the Write tool is blocked on workflow files):
  - a `changes` job (path filter `fep-service/`);
  - a `test` job (Java 21, `./mvnw test`);
  - a `gate` job that passes on success or skipped, fails otherwise;
  - `permissions: contents: read`.
- Not added to the ruleset's required checks; that's the user's call, noted in the PR.

## 7. Docs
- `docs/card-api-reference.html` and the docs-site copy:
  - internal endpoints now need `FEP_SERVICE`;
  - the dead path removed;
  - the BIN "none" auth corrected (they need a JWT).
- Postman, if it has internal or BIN requests.
- `docs/integration-runbook.md`: the fep credential (set-up, existing realm, rotate, verify), the ingress blackhole, and the NetworkPolicy assumptions.
- `docs/deferred-backlog.md`: item 11 marked done.
- `CLAUDE.md`: card-service security notes, fep-service notes, versions rows.
- `cba-log.md` cont. 34.

## 8. Verification
1. On the branch, before any fix: write the card-service IT and confirm that today `POST /api/v1/internal/detokenize` with **no token** returns 200.
2. `card-service: ./mvnw -o clean verify -Pfull-integration`; `fep-service: ./mvnw -o test`; `backend: ./mvnw -o test` (realm guard). All green.
3. Real Keycloak token check; validate the k8s YAML.
4. Gate grep (tracked + untracked) → docs → PR → CI green → merge on the user's word.
