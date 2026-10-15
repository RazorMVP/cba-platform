> **Status:** merged — PR #154, merge commit `0ba2ff5`. Approved 2026-10-15.  
> Recovered verbatim from the session transcript (the approved `ExitPlanMode` plan); the body below is unedited.

# Backlog 12 — card-service → backend: service token, the balance endpoint, and the card-authorization money bugs

## Context
Card authorizations for **DEBIT** and **CREDIT** cards ask the backend for funds (`CardAuthorizationService.getDebitBalance` / `getCreditAvailability`, card-service). Every one of these calls fails today:
1. **No token.** `backendRestTemplate` is `new RestTemplate()` (no auth, no timeouts), and the backend requires a Keycloak JWT.
2. **No endpoints.** The backend has neither `GET /api/v1/accounts/{id}/balance` nor `/api/v1/loans/{id}/credit-availability`.
3. **Contract mismatch.** card-service reads a flat `{availableBalance, currencyCode}`, but every backend response is wrapped in `ApiResponse {data, meta, errors}`.

The result: every debit and credit authorization declines with RC 91. That also hides two real money defects that surface once the calls work:
- **×100 units bug.** `CardAuthRequest.amount` is in **minor units** (DE4, cents), and it's compared directly with the backend balance in **major units**. The **prepaid wallet** path has the same mix (wallet balance 50 000.00 vs cents). fep-service's DE54 builder treats the returned balance as major units (`movePointRight(2)`), so only the comparison is wrong.
- **No currency check.** The DE49 numeric code ("840") is never compared with the account's alphabetic currency ("USD").

More found in exploration:
- **The backend RestTemplate is shared with external settlement.** `SettlementFileTransmitter` injects the same bean by type and POSTs settlement files to PAPSS with `setBearerAuth(apiKey)`. A token interceptor on it would leak card-service's Keycloak token to an external clearinghouse, or overwrite the PAPSS key. So the two must be split first.
- **No credit-line model exists in the backend.** Loans are single-disbursement amortising term loans: no credit limit, no revolving draws. "Available credit" can't be computed from anything real.
- **Backend rate limiting would throttle authorizations.** `RateLimitFilter` limits all `/api/v1/**` per JWT `azp`. Every authorization would share one BASIC bucket (100/min), and a 429 becomes RC 91.

card-service is on Spring Boot 3.5.16 / Security 6.5, so it can reuse the backend's cont. 33 pattern (`OAuth2ClientHttpRequestInterceptor` + `AuthorizedClientServiceOAuth2AuthorizedClientManager`, `backend/.../config/CardServiceConfig.java`).

Branch `fix/card-service-backend-token` off `main` (`d866430`), one PR. Merge only on the user's word with CI green.

## Decisions (recommended defaults; the user may change them)
- **D1. Credit cards: no credit-availability endpoint now.** Credit authorizations decline explicitly with **RC 57** ("transaction not permitted"; log: credit lines not modelled) instead of failing into RC 91.
  - A real revolving credit line becomes a new backlog item: a model plus GL (credit card draws = loan disbursements), and IFRS 9 treatment of undrawn commitments (ECL on undrawn exposure, IFRS 9 §2.1(g)/§5.5.20).
  - That needs the standards check before any recommendation, per the standing rule.
- **D2. Least privilege:** a new realm role **`CARD_SERVICE`** for client `cba-card-service`. The backend admits it only on the new balance endpoint (URL matcher before the `GET /api/v1/**` catch-all, plus `@PreAuthorize`).
- **D3. Rate limiting:** `RateLimitFilter` skips `GET /api/v1/accounts/*/balance`. It's a cheap read behind a role check, and authorizations must not be throttled into declines.
- **D4. Currency and units:**
  - card-service converts the DE4 minor-unit amount to major units with the DE49 currency's ISO 4217 fraction digits (`java.util.Currency`, numeric code → instance);
  - a transaction currency that differs from the account's (or wallet's) currency → **RC 57** (no FX on the authorization path; the scheme's cardholder-billing amount DE6 is a follow-up).
- **D5. No per-read audit row in the backend.** The backend audits state changes only, and card-service already writes every authorization to `authorization_log`. A backend audit write on every card transaction is not added.

## 1. Backend
- **`AccountService`:** extract the read-only spend calculation from the private `checkDebitAllowed` into `public CardFunds cardFunds(UUID accountId)`. It returns:
  - `accountId`, `currencyCode`;
  - `availableBalance` = balance − ACTIVE holds − `computeEffectiveFloor()` (the floor already folds in the overdraft), floored at 0;
  - `debitAllowed`, plus `reason` (`ACCOUNT_NOT_ACTIVE` | `ACCOUNT_IN_LOCKIN_PERIOD` | null). Lock-in counts only when global config `enforce-lockin-period-withdrawal` is on, as `withdraw()` does.
  - `checkDebitAllowed` reuses the same pieces, so the formula lives in one place (existing debit tests must stay green).
- **`AccountController`:** `GET /api/v1/accounts/{id}/balance` → `ApiResponse<CardFunds>`, `@PreAuthorize("hasRole('CARD_SERVICE') or hasAnyRole('ADMIN','TELLER')")`. No PII (no customer name). Unknown id → 404.
- **`SecurityConfig`:** `.requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/balance").hasAnyRole("CARD_SERVICE","ADMIN","TELLER")` before the `GET /api/v1/**` rule.
- **`RateLimitFilter`:** skip that path (D3).

## 2. card-service
- **Split the RestTemplates.**
  - `settlementRestTemplate` (plain, with timeouts) is injected into `SettlementFileTransmitter` with an explicit qualifier. Check its constructor and the Lombok-`@Qualifier` gotcha; keep the tests' 2-arg constructor.
  - `backendRestTemplate` is for backend calls only, with 2 s connect / 3 s read timeouts (inside fep's 5 s budget; `authorize()` holds a DB transaction during the call).
- **New `BackendClientConfig`** (or extend `RestClientConfig`): when `backend.auth.client-secret` is set, add an `OAuth2ClientHttpRequestInterceptor` with:
  - fixed registration id `backend` and principal `cba-card-service`;
  - `token-uri`;
  - the token `RestClient` with the documented converters plus timeouts;
  - an `OAuth2AuthorizationException` turned into a `ResourceAccessException`;
  - a blank secret → no interceptor plus a WARN (dev bypass, tests).
  - `pom.xml`: `spring-security-oauth2-client`, BOM-managed (6.5.11, already in `~/.m2`).
- **`CardAuthorizationService`:**
  - Parse the `ApiResponse` envelope (`exchange` + `ParameterizedTypeReference<Envelope<BackendFunds>>`).
  - Debit: `debitAllowed == false` → RC 57; currency mismatch → RC 57; amount converted to major units, then RC 51 if short.
  - Prepaid: the same unit conversion and currency check against the wallet.
  - Credit: RC 57 without calling the backend (D1); `getCreditAvailability` removed.
  - Balance inquiry stays as is: `availableBalance` is still major units for fep's DE54.
- **`application.yml`:** `backend.auth.{client-id: ${CARD_SERVICE_CLIENT_ID:cba-card-service}, client-secret: ${CBA_CARD_SERVICE_SECRET:}, token-uri: ${BACKEND_TOKEN_URI:http://localhost:8180/realms/cba/protocol/openid-connect/token}}`.

## 3. Keycloak, infrastructure, demo data
- **Realm** (both copies): role `CARD_SERVICE`; client `cba-card-service` (service accounts only, `${CBA_CARD_SERVICE_SECRET}`); service account holds `CARD_SERVICE` only.
- **`KeycloakRealmServiceClientTest`:** add the third row.
- **Compose:** `CBA_CARD_SERVICE_SECRET` for Keycloak and card-service (local default), plus `BACKEND_TOKEN_URI` for card-service.
- **k8s:** `keycloak-secret` and `card-service-secret` keys; Keycloak and card-service deployments get the env; the card-service configmap gets the token URI.
- **`.env.example`.**
- **Demo data (if a matching backend demo account exists):** the card-service demo debit card links to `bbbbbbbb-…`, which exists nowhere in the backend. A card-service migration relinks it to a real backend V2 demo USD account, so the terminal-simulator debit demo can approve. Otherwise this is documented as a follow-up.

## 4. Tests
- **Backend unit `AccountServiceTest`:** `cardFunds` covers holds, minimum balance, overdraft (negative floor), FROZEN/DORMANT → `debitAllowed=false`, lock-in on/off, floored at 0. Existing withdraw/hold tests stay green.
- **Backend IT** (`CardFundsIT`, real PG):
  - seeded account + hold → `GET /api/v1/accounts/{id}/balance` returns the envelope and spendable amount;
  - unknown → 404;
  - controller call with `ROLE_CARD_SERVICE` → OK, with `ROLE_CUSTOMER` → `AccessDeniedException`;
  - `ROLE_CARD_SERVICE` on another GET (e.g. `getAccount`) → denied.
  - `SecurityConfigTest`/URL matcher check if feasible.
- **card-service unit `CardAuthorizationServiceTest`**, rewritten stubs (envelope):
  - debit approves 25.00 against 500.00 with amount 2500 minor (it declined before);
  - RC 51 when short;
  - `debitAllowed=false` → 57;
  - currency mismatch → 57;
  - prepaid unit fix;
  - credit → 57 with no backend call;
  - JPY (0 fraction digits) case;
  - backend down → 91.
- **card-service WireMock test** (backend + token endpoint, like `CardServiceClientIntegrationTest`): Bearer sent, token fetched once, envelope parsed.
- **Settlement:** a guard test that `SettlementFileTransmitter` doesn't get the token interceptor (its client has no OAuth interceptor). Existing settlement tests stay green.
- **Realm guard** (backend), **real Keycloak 23** check (token roles `["CARD_SERVICE"]`), and **kubeconform** on changed manifests.

## 5. Docs
- `docs/api-reference.html` + Postman (+ docs-site copies): the new balance endpoint (CARD_SERVICE).
- `docs/card-api-reference.html`: authorization RC semantics (51/57/91), the units note, and credit cards → 57.
- `docs/integration-runbook.md` §9/9a: the third credential.
- `docs/deferred-backlog.md`: 12 ✅, plus new items:
  - credit-line model (IFRS 9 research first);
  - card auth holds and GL posting of card settlement (approvals reserve no funds; settlement moves no backend money);
  - the `RateLimitFilter` tier taken from an unverified `X-Rate-Tier` header (security);
  - DE6 billing-amount FX.
- `CLAUDE.md`, `cba-log.md` cont. 35.

## 6. Verification
1. **Baseline first:** a WireMock test on unchanged card-service shows no `Authorization` header, and a debit of 25.00 against a 500.00 balance declines RC 51 (the units bug).
2. `backend ./mvnw -o clean verify -Pfull-integration`, `card-service ./mvnw -o clean verify -Pfull-integration`, `fep-service ./mvnw -o test`: all green.
3. Real Keycloak token check; kubeconform; compose config.
4. Gate grep (tracked + untracked) → docs → PR → CI green → merge on the user's word.
