# Backlog 13: Open Banking: TPPs read through the consent; consents bound to TPP and customer

> Status: approved 2026-11-03 (user: "13"); no D1/D2 reply → defaults **D1a + D2a**.

## Context
Backlog 13 says AISP takes a TPP token's `sub` as the customer, so TPPs see no accounts. Reading the code shows more:
- TPPs today get a **500**, not an empty list.
- Nothing ties a consent to the TPP using it or to the customer who authorises it.

Once AISP starts returning data, those gaps let any TPP read, and (through PISP) move, money in any customer's accounts. Goal: a TPP reaches customer data only through its own consent that is authorised, unexpired, and carries the right scope, and only for that consent's customer.

## What was checked (read-only)
| # | Finding | Where |
|---|---|---|
| 1 | `ROLE_API_CLIENT` comes only from a partner API key (`Authorization: ApiKey cba_…`), plus dev bypass. Principal = orgId **string**; `PartnerPrincipal` sits on details. No Keycloak client holds the realm role `API_CLIENT` | `PartnerApiKeyAuthFilter.java:70-90`, `cba-realm.json` |
| 2 | AISP takes `@AuthenticationPrincipal Jwt jwt`, which is null for an API key, so `jwt.getSubject()` throws an NPE and TPPs get **500** on all 3 endpoints. The existing test fakes a JWT for `API_CLIENT`, so it never caught this | `AccountInfoController.resolveCustomerId`, `AccountInfoControllerTest.apiClient_unchanged` |
| 3 | AISP never looks at a consent: no consent id, no status/expiry/scope check | `AccountInfoController` |
| 4 | `POST /consents` takes `tppClientId` from the body, so a TPP can create a consent in another TPP's name. `GET`/`DELETE /consents/{id}` have no ownership check | `ConsentController`, `ConsentService.createConsent` |
| 5 | `PUT /consents/{id}/authorise` lets **any** CUSTOMER authorise **any** consent, including one naming another customer | `ConsentService.authoriseConsent` |
| 6 | PISP `validatePispConsent` checks status/scope/expiry only, not that the caller is the consent's TPP or that `sourceAccountId` belongs to the consent's customer. Any authorised payments consent can debit any account. `GET /domestic-payments/{id}` returns any payment | `PispController`, `ConsentService.validatePispConsent` |
| 7 | CBPII `confirmFunds`: same gaps. Any account's balance can be probed, and so can any card's (card fallback has no owner check) | `ConsentService.confirmFunds` |
| 8 | Scopes are free strings. Postman sends `["accounts","payments"]`; the catalogue is `accounts_read`, `balances_read`, `transactions_read`, `payments`, `fundsconfirmation`, `card_read`, `card_balances_read`, `card_transactions_read` | `ConsentScope`, Postman |
| 9 | Patterns to reuse: `PartnerSecurity.currentOrgId()` (org from API key or partner JWT); `PartnerService.revokeConsentForOrg` (404 when the org doesn't own the consent); `Payment.createdBy` = `open-banking:<consentId>` for PISP payments | `PartnerSecurity.java:44`, `PartnerService.java:255`, `PaymentReversalPartnerNotifier` |
| 10 | The URL rule lets only CUSTOMER/API_CLIENT reach `/open-banking/**`; ADMIN in the `@PreAuthorize`s only works under dev bypass | `SecurityConfig.java:103` |

## Decisions for you
**D1 — scope of this PR**
- **D1a (recommended):** bind consents everywhere: AISP, the consent lifecycle, PISP and CBPII. One shared helper, one PR. Findings 4–7 become exploitable the moment AISP works, and PISP moves money.
- **D1b:** AISP plus the consent lifecycle (findings 2–5) only. PISP/CBPII binding becomes a new backlog item. The payment hole stays open meanwhile.

**D2 — unknown scopes at consent creation**
- **D2a (recommended):** `400 INVALID_SCOPE` for anything outside `ConsentScope`. Postman/docs move to the catalogue names.
- **D2b:** keep accepting any string. Only catalogue names grant access, so `"accounts"` silently grants nothing.

Default if approved without a reply: **D1a + D2a**.

## Design
**Who the TPP is:** `PartnerSecurity.currentOrgId()`. An `API_CLIENT` caller without an org gets `403 TPP_UNIDENTIFIED`. A consent belongs to the TPP when `tppClientId == orgId.toString()`. This is the rule the partner consents API already uses.

**New `ConsentService.requireUsableConsent(consentId, ConsentScope... anyOf)`** (read-only). It returns the consent; its customer id is fetched in the same transaction. Checks:
- unknown, or another org's → `404 CONSENT_NOT_FOUND`, for anti-enumeration, like `revokeConsentForOrg`;
- not AUTHORISED → `403 CONSENT_NOT_AUTHORISED`;
- expired by date or status → `403 CONSENT_EXPIRED`;
- has none of the scopes → `403 CONSENT_SCOPE_MISSING`.

`CbaException` gets a `forbidden(code, message)` overload; today its forbidden is always `ACCESS_DENIED`.

## Steps (branch `fix/open-banking-consent-binding`, one PR)
1. **AISP** (`AccountInfoController`):
   - drop `@AuthenticationPrincipal Jwt`. CUSTOMER keeps the `customers.keycloak_id` path. API_CLIENT must send header **`x-consent-id`** (missing → `400 CONSENT_ID_REQUIRED`), and the customer comes from the consent.
   - Scopes (TPP only):
     - `/accounts`: bank accounts if `accounts_read`, cards if `card_read`, neither → 403;
     - balances: bank `balances_read`, card `card_balances_read`;
     - transactions: bank `transactions_read`, card `card_transactions_read`.
   - Ownership stays as it is (404).
2. **Consent lifecycle** (`ConsentController` / `ConsentService`):
   - create = API_CLIENT only. `tppClientId` is the caller's org; a different body value → `400 TPP_CLIENT_MISMATCH`, and an omitted one is filled in. The customer must exist (unchanged). D2a scope check.
   - `GET` / `DELETE`: the consent's TPP or the consent's customer, else 404.
   - authorise = CUSTOMER whose `customers.keycloak_id` is the consent's customer, else 404.
3. **PISP** (`PispController`):
   - API_CLIENT only;
   - `requireUsableConsent(id, PAYMENTS)`;
   - `sourceAccountId` must belong to the consent's customer (404 `ACCOUNT_NOT_FOUND`), checked before `paymentService.transfer`;
   - `GET /domestic-payments/{id}` → 404 unless `payment.createdBy` is `open-banking:<consentId>` of a consent owned by the caller's org.
4. **CBPII** (`ConsentService.confirmFunds`):
   - API_CLIENT only;
   - `requireUsableConsent(id, FUNDS_CONFIRMATION)`;
   - the bank account or the card must belong to the consent's customer (card via `CardServiceClient.getCard` → `customerId`), else 404.
   - The card path still also needs `card_read` or `card_balances_read`.
5. `validatePispConsent` is replaced by `requireUsableConsent`. Its error codes change from `ACCESS_DENIED` / `400 CONSENT_EXPIRED` to the codes above, matching what partner-docs already promise (`403`).

## Tests
- `AccountInfoControllerTest`, authenticated the way the real filter does it (`UsernamePasswordAuthenticationToken` + `PartnerPrincipal`, so the NPE can't hide again):
  - TPP + own authorised consent → customer's accounts;
  - no header → 400;
  - another org's consent → 404;
  - revoked / expired / awaiting → 403;
  - scope split (bank-only, card-only, none);
  - balances/transactions of an account outside the consent's customer → 404;
  - CUSTOMER path unchanged.
- `ConsentServiceTest`:
  - create binds the org, mismatch → 400, D2a;
  - get/revoke by another org → 404;
  - authorise by the wrong customer → 404;
  - `requireUsableConsent` matrix;
  - CBPII foreign account/card → 404.
- New `PispControllerTest`: foreign source account → 404 and `transfer` never called; foreign consent → 404; GET of another TPP's payment → 404.
- Integration (`-Pfull-integration`): one HTTP IT with a real partner API key, `x-consent-id`, AISP → 200 with the customer's accounts, and another org's key → 404. OpenAPI snapshot regenerated (new header).

## Verification
- `cd backend && ./mvnw -o test`, then `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw clean verify -Pfull-integration`. Must be green; I'll report the new count against the last 955.
- PR CI: Backend CI gate, CodeQL, Gitleaks, Dependency Review.

## Docs (same PR)
- `docs/api-reference.html` + static copy: Open Banking section, covering the `x-consent-id` header, TPP binding, scopes per endpoint, the new error codes, and API_CLIENT-only PISP/CBPII.
- Postman + static copy: consent body with catalogue scopes and no `tppClientId`; `x-consent-id` on the AISP requests; error examples.
- `partner-docs/docs/open-banking.md`: correct the AISP paths (`/aisp/accounts`, `/consents`), the header and the scope names.
- `CLAUDE.md`: Open Banking module note (consent binding rules) and backend row (cont. 55).
- `cba-log.md`: cont. 55 with the gate grep.
- `docs/deferred-backlog.md`: item 13 → ✅ DONE.
- `docs/plans/2026-11-03-open-banking-consent-binding.md` + README row.
- Merge only when you say "merge <PR>" and CI is green.

## Not in this PR (noted)
- The web Open Banking admin screen calls `GET /consents` (no such endpoint) and `?command=authorise|revoke` (the endpoints are `PUT …/authorise` / `DELETE`). It's already broken, unchanged here; offered as a follow-up.
- PISP consents carry no instructed amount or creditor (UK OB domestic-payment-consent). A payment within an authorised consent can be any amount; that's a separate item.
