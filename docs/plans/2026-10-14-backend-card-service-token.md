> **Status:** merged — PR #152, merge commit `a894869`. Approved 2026-10-14.  
> Recovered verbatim from the session transcript (the approved `ExitPlanMode` plan); the body below is unedited.

# Backend → card-service: service token (client credentials) + the defects blocking card Open Banking

## Context
The backend's `openbanking.card.CardServiceClient` calls card-service without an `Authorization` header (`config.CardServiceConfig`'s `cardServiceRestTemplate` has no interceptor). card-service's catch-all chain requires a Keycloak JWT and `@PreAuthorize("hasAnyRole('ADMIN','TELLER')")`, so every call gets 401. The client is fail-safe: it logs a WARN and returns empty, so Open Banking AISP card accounts, balances and transactions, and the CBPII card funds check, silently return nothing. Docker hides this because card-service runs there with `APP_AUTH_BYPASS=true`; k8s and prod would get 401.

The read-only exploration found three more defects on the same path:
1. **`GET /api/v1/cards/{id}/authorizations` doesn't exist in card-service** (`CardServiceClient:136`). Card transactions stay empty even with a token, and with a JWT the call becomes a 500 via card-service's catch-all handler.
2. **AISP resolves the customer as `UUID.fromString(jwt.sub)`** (`AccountInfoController.resolveCustomerId:183`). But `sub` is the Keycloak user id, and the backend maps it to `customers.keycloak_id` everywhere else (`config.CustomerAccess.callerCustomerId()`). A real customer token therefore sees no bank accounts and no cards.
3. **The backend `dev`/`prod` profiles override `card.service.base-url` with `${CARD_SERVICE_HOST}`** (no default in prod), but compose and k8s only set `CARD_SERVICE_BASE_URL`. In prod on k8s the placeholder can't be resolved.

Verified with Context7:
- **Spring Security 6.5 `OAuth2ClientHttpRequestInterceptor`** is a `ClientHttpRequestInterceptor`, so it works on `RestTemplate`.
  - Its default resolvers read per-request attributes, which `RestTemplate` doesn't have.
  - So it needs a fixed `ClientRegistrationIdResolver` and `PrincipalResolver`, or it silently adds nothing.
- **Keycloak `--import-realm`** resolves `${ENV_VAR}` placeholders in realm JSON, so the client secret stays out of git.

Branch `feat/card-service-client-credentials` off `main` (`1960dcf`), one PR. Merge only on the user's word with CI green.

## Decisions (recommended defaults; the user may change them)
- **D1. Least privilege:** a new realm role **`CARD_READER`**, given **only** to the backend's service account and added to card-service's 4 read endpoints. Do **not** grant TELLER: TELLER can issue, block and cancel cards, and the backend only reads.
- **D2.** Fix AISP customer resolution for **CUSTOMER** tokens via `CustomerAccess.callerCustomerId()` (otherwise the token fix shows nothing). `API_CLIENT` (TPP) tokens need consent→customer resolution, which is a separate design task (follow-up; behaviour unchanged).

## 1. Keycloak realm (`infrastructure/keycloak/cba-realm.json` + k8s copy `infrastructure/k8s/keycloak/keycloak-realm-configmap.yaml`)
- **Realm role `CARD_READER`** ("service-to-service: read card data; never assign to people").
- **New confidential client `cba-backend-service`:**
  - `serviceAccountsEnabled: true`;
  - standard, implicit, direct-access and device flows all off;
  - `secret: "${CBA_BACKEND_SERVICE_SECRET}"` (env placeholder).
- **Service-account user** `service-account-cba-backend-service` with realm roles `[CARD_READER]`.
- Keep `cba-backend` bearer-only, untouched.

## 2. Backend
- `pom.xml`: `org.springframework.security:spring-security-oauth2-client`, BOM-managed. This is the library only, not the Boot starter, so no global client auto-configuration kicks in.
- `CardServiceConfig` (the existing bean, extended):
  - Properties `card.service.auth.client-id` (default `cba-backend-service`), `client-secret` (default empty), and `token-uri`. Token URI rather than issuer, so there is no OIDC discovery at startup (the same lesson as `jwk-set-uri`).
  - **Secret set:** build a `ClientRegistration` (`client_credentials`) in an `InMemoryClientRegistrationRepository`, plus `AuthorizedClientServiceOAuth2AuthorizedClientManager` (works outside a web request and caches the token until expiry).
  - Use `OAuth2ClientHttpRequestInterceptor` with:
    - a fixed registration id `card-service`;
    - a fixed principal `cba-backend`, so one token is shared app-wide rather than one per customer.
  - Wrap the interceptor so an `OAuth2AuthorizationException` (Keycloak down or a bad secret) becomes a `RestClientException`, keeping the client's existing fail-safe empty result instead of a 500.
  - **Secret blank:** no interceptor and a one-time WARN "card-service calls are unauthenticated" (dev with the bypass, tests).
- `application.yml`:
  - `card.service.auth.*` with env vars `CARD_SERVICE_CLIENT_ID`, `CBA_BACKEND_SERVICE_SECRET`, `CARD_SERVICE_TOKEN_URI` (default `http://${KEYCLOAK_HOST:localhost}:${KEYCLOAK_PORT:8180}/realms/cba/protocol/openid-connect/token`).
  - dev and prod: `card.service.base-url: ${CARD_SERVICE_BASE_URL:http://${CARD_SERVICE_HOST:localhost}:${CARD_SERVICE_PORT:8081}}` (fixes defect 3).
- `AccountInfoController.resolveCustomerId`:
  - CUSTOMER uses `customerAccess.callerCustomerId()`, and no customer record → 404 `CUSTOMER_NOT_FOUND`.
  - Other roles keep the current `sub` behaviour (documented follow-up).

## 3. card-service
- `CardController`: add `GET /api/v1/cards/{id}/authorizations`, which returns `ApiResponse<List<AuthorizationLog>>` via the existing `CardAuthorizationService.getHistory(id)` (the same call `CardApiController:188` uses).
- `@PreAuthorize("hasAnyRole('ADMIN','TELLER','CARD_READER')")` on the 4 read endpoints the backend calls: `listCards`, `getCard`, `getBalance`, and the new `authorizations`. No other endpoint changes.

## 4. Infrastructure
- `docker-compose.yml`:
  - Keycloak and backend get `CBA_BACKEND_SERVICE_SECRET: ${CBA_BACKEND_SERVICE_SECRET:-local-dev-only-change-me}` (existing pattern, cf. `KEYCLOAK_ADMIN_PASSWORD:-admin`).
  - The backend also gets `CARD_SERVICE_TOKEN_URI: http://keycloak:8180/realms/cba/protocol/openid-connect/token`, so the token's `iss` matches card-service's issuer.
- k8s:
  - `secrets/platform-secrets.yaml` gets `CBA_BACKEND_SERVICE_SECRET: <CHANGE_ME>` for the backend and Keycloak.
  - Backend deployment env from the secret; backend configmap gets the token URI.
  - Keycloak deployment env from the secret.
- `.env.example`: the new var.

## 5. Tests
- **Backend unit** (`CardServiceConfigTest`, `MockRestServiceServer`; a stub `OAuth2AuthorizedClientManager`):
  - the Bearer header is sent;
  - secret blank → no header;
  - token failure → `RestClientException`, and the client returns empty.
- **Backend IT** `CardServiceClientIT`, using the existing `HttpProvidersWireMockIntegrationTest` pattern (WireMock container plus `/__admin/mappings`):
  - one WireMock serves the token endpoint and card-service;
  - the card stubs require `Authorization: Bearer <token>`;
  - cards, balance and authorizations are mapped;
  - the token is fetched **once** across calls (WireMock request count).
- **Backend:** AISP customer resolution (`sub` → keycloak_id customer; unknown → 404).
- **card-service IT**, extending `RealmRoleMappingIntegrationTest` with `Bearer test-roles.CARD_READER`:
  - the 4 read endpoints are allowed;
  - a write endpoint (`POST /api/v1/cards/{id}?command=block`) and `/api/v1/bins` → 403;
  - the new authorizations endpoint returns the envelope with `financial` and `createdAt`.
- **Realm guard test** (backend or a script in CI-run tests) parses `cba-realm.json` and the configmap copy:
  - client `cba-backend-service` has `serviceAccountsEnabled`;
  - its secret is a `${…}` placeholder (never a literal);
  - the service account has only `CARD_READER`.
- Regenerate both OpenAPI snapshots (card-service: new path). The OWASP gate must stay green with the new jar (Spring Security suppressions are group-level).

## 6. Docs
- `docs/card-api-reference.html`, Postman card-service section, and both docs-site copies: the new endpoint plus `CARD_READER` on the 4 endpoints.
- `docs/api-reference.html`: AISP note (customer resolution; card data needs the service credential).
- `docs/integration-runbook.md`: the `cba-backend-service` credential — create or rotate the secret, env vars, verify.
- `CLAUDE.md`:
  - Security/RBAC: the `CARD_READER` service role, and the follow-up line removed;
  - backend Card Service Integration gotchas;
  - versions rows.
- `cba-log.md` cont. 33 with the gate grep proof.

## 7. Out of scope — flagged as follow-ups (not in this PR)
- **⚠️ Security:** card-service's `/api/v1/internal/**` (the fep-service chain, including `/detokenize` and `/authorize`) is `permitAll` on the grounds that "network isolation is the guard". But the k8s ingress routes `/` to card-service and there is no NetworkPolicy, so it is publicly reachable. **Recommend this as the very next task.**
- The card-service → backend direction: `backendRestTemplate` sends no token, and the endpoints it calls (`/api/v1/accounts/{id}/balance`, `/api/v1/loans/{id}/credit-availability`) don't exist in the backend.
- AISP for `API_CLIENT` (TPP) tokens needs consent→customer resolution.
- `cba-web` has a literal secret `cba-web-dev-secret` in the realm JSON (dev-only, public repo).

## 8. Verification
1. Branch; baseline from main's green CI (`1960dcf`).
2. Write the WireMock IT first and confirm that today's client sends no `Authorization` (the stub doesn't match, so the result is empty).
3. `backend: ./mvnw -o clean verify -Pfull-integration` and `card-service: ./mvnw -o clean verify -Pfull-integration`: all green.
4. Optional local end-to-end in compose: start Keycloak + card-service with `APP_AUTH_BYPASS=false` + the backend, then call AISP `/accounts` with a customer token; card accounts appear.
5. Gate grep (tracked + untracked) → docs → PR → CI green (OWASP, SpotBugs) → merge on the user's word.
