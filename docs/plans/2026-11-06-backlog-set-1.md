# Backlog set 1 — items 24, 23, 3, 17, 10

> Status: approved 2026-11-06 (D1–D6 recommended; D7 → b). PR A (24 + 23 + 3) in progress; PRs B and C follow.
> On approval this file is copied to `docs/plans/2026-11-06-backlog-set-1.md` and committed with PR A.
> The previous content of this file (backlog 13) is in `docs/plans/2026-11-03-open-banking-consent-binding.md` (#224).

## Context
The backlog is being worked in sets so it doesn't take a year. Set 1 is five items that can be built now. None is blocked or needs outside sign-off.

| # | Problem today | Outcome |
|---|---|---|
| 24 | An authorised `payments` consent lets the TPP send **any amount to anyone** from the customer's accounts, repeatedly, until the consent expires | The consent names the amount, currency and creditor; the payment must match it; the consent can be used **once** |
| 23 | The staff Open Banking screen calls endpoints that don't exist (`GET /consents`, `?command=`), and `/open-banking/**` refuses staff anyway. The TPP registry screen calls `/openbanking/tpp`, which doesn't exist either | Staff can list, view and revoke consents through `/api/v1/open-banking/consents`. The TPP registry runs on partner orgs, with approve, reject, suspend and reactivate, and suspension is actually enforced |
| 3 | `ACCOUNT.ACCESS_GRANTED` and `ACCOUNT.BALANCE_UPDATED` are in the event catalogue but never fire | Both fire. Balance events are coalesced, so a busy account doesn't flood a TPP |
| 17 | A card used abroad declines RC 57: the merchant's currency (DE49) is compared with the account's | Authorize, hold, debit, clear and refund in the cardholder billing amount (DE6/DE51) when the scheme sends it |
| 10 | Report SQL runs as the app user (a **superuser**) behind a keyword filter, and report parameters are pasted into the SQL text, so TELLERs can inject SQL today | Report SQL runs as a read-only login role that sees only a `reporting` schema of views, with bound parameters, a timeout and a row cap. ADMINs can then create reports, and a second ADMIN enables them |

Recommended packaging: **3 PRs**, each merged when you say "merge N" and CI is green:

| PR | Items | Scope |
|---|---|---|
| **A** | 24 + 23 + 3 (Open Banking) | All in `openbanking` / `ConsentService` |
| **B** | 17 (card FX) | fep-service, card-service, web simulator |
| **C** | 10 (reports sandbox) | backend, infra, web |

## Decisions for you
Reply "go" to take every recommendation, or name a decision to change it (e.g. "D2 b").

**D1 (item 24): may a `payments` consent also carry other scopes?**
- **a (recommended):** no. A payment consent is single-purpose, like UK OB's domestic-payment-consent. Mixing scopes returns `400 PAYMENT_CONSENT_SCOPES`.
- b: allow mixing; the initiation applies only to the payment part.

**D2 (item 3): what does `ACCOUNT.BALANCE_UPDATED` carry?**
- **a (recommended):** a signal only: `{consentId, accountId, updatedAt}`. The TPP then re-reads the balance through AISP, so no data leaves outside the consent checks.
- b: include the balance and the available balance.

**D3 (item 3): `ACCOUNT.ACCESS_GRANTED`, one event or many?**
- **a (recommended):** one per consent, carrying the list of accounts in scope.
- b: one event per account.

**D4 (item 17): which currency does the scheme settle a foreign card transaction in?**
- **a (recommended):** the billing currency (DE51), which is the account's currency. Settlement currency (DE5/DE50) waits for backlog 15d.
- b: block item 17 until 15d is done.

**D5 (item 10): how is a report's SQL shape checked?**
- **a (recommended):** JSqlParser 5.x. Its licences (LGPL-2.1 or Apache-2.0) aren't on our GPL/AGPL deny-list. It enforces: one `SELECT`, no `INTO`, no data-modifying CTE, and a function deny-list. **The DB role is the real security boundary**; the parser is a second layer.
- b: DB role only, keeping the keyword filter just for error messages.

**D6 (item 10): who approves a new report?**
- **a (recommended):** reports are created **disabled**, and a *different* ADMIN enables them. The creator trying to enable gets `403 SAME_USER`.
- b: route it through the full maker-checker module.
- c: one ADMIN is enough.

**D7 (item 23): `/admin/open-banking` ("TPP registry") is a second broken screen; it calls `/openbanking/tpp`, which doesn't exist.**
- **Chosen: b (user, 2026-11-06): fix it in PR A.** TPPs are partner orgs, so the screen is rewired to `/api/v1/partners` (see "23b" below).

D1–D6: the recommended options were accepted ("everything is fine but for D7").

---

## PR A — Open Banking (24 + 23 + 3), branch `feat/open-banking-set1`

### 24 — a payment consent carries the initiation and is used once

**Schema (migration V65, `open_banking_consents`)**
- Add `instructed_amount NUMERIC(19,4)`, `instructed_currency VARCHAR(3)`, `creditor_account_id UUID`, `debtor_account_id UUID NULL`, `payment_reference VARCHAR(140)`, `payment_id UUID`, `consumed_at TIMESTAMPTZ`, `version BIGINT DEFAULT 0`.
- A check constraint: the instructed fields are either all set or all empty.

**New status `ConsentStatus.CONSUMED`**
- `expireDueConsents` leaves consumed consents alone.
- `requireUsableConsent` returns `403 CONSENT_CONSUMED` for them.

**Consent creation (`ConsentRequest`)** gains an optional `initiation`:
- Fields: `instructedAmount` (> 0), `currency` (ISO alphabetic), `creditorAccountId`, `debtorAccountId` (optional), `reference` (optional).
- `payments` scope without an initiation → `400 PAYMENT_INITIATION_REQUIRED`.
- An initiation without the `payments` scope → `400 INITIATION_WITHOUT_PAYMENTS`.
- D1a applies.
- The creditor account must exist → otherwise `400 CREDITOR_ACCOUNT_NOT_FOUND`.
- A debtor account, if given, must belong to the consent's customer → otherwise `404 ACCOUNT_NOT_FOUND`.
- `ConsentResponse` returns the initiation, `paymentId` and `consumedAt`.

**Payment: new `ConsentService.initiatePayment(consentId, DomesticPaymentRequest)`**, `@Transactional`. It replaces `authorisePaymentInitiation`:
1. Lock the consent row with a new `ConsentRepository.findByConsentIdForUpdate`, copying the `AccountRepository.findByIdWithLock` pattern. Then run the existing TPP, status, expiry and scope checks.
2. If the consent is CONSUMED and the request equals the initiation, return the original payment (`200`, same `domesticPaymentId`). That makes a TPP retry safe. Any other request against a consumed consent → `403 CONSENT_CONSUMED`.
3. Check the request against the consent: amount, currency, destination, and the debtor if one was set. A mismatch → `400 PAYMENT_DOES_NOT_MATCH_CONSENT`, naming the field. The source account must be the customer's and in the instructed currency, otherwise `400 CURRENCY_MISMATCH` (today the currency is only echoed back).
4. Call `paymentService.transfer(...)` in the same transaction. `PaymentService` throws on every refusal (checked), so a failure rolls everything back and the consent stays AUTHORISED.
5. Set the consent to CONSUMED, with `payment_id` and `consumed_at`.

**Callers and existing data**
- `PispController` calls `initiatePayment`. Webhook events are unchanged.
- Existing `payments` consents have no initiation, so they can no longer be used to pay (`403 CONSENT_HAS_NO_INITIATION`). They fail closed; no data fix is needed.

### 23 — staff consent endpoints and a working web screen

**New `StaffConsentController` at `/api/v1/open-banking/consents`**
- Returns `ApiResponse` with paging (the `LoanController` pattern).
- `GET ?customerId&tppClientId&status&type&page&size` — ADMIN, TELLER. `type` maps to a scope: PISP = `payments`, CBPII = `fundsconfirmation`, AISP = the rest.
- `GET /{consentId}` — ADMIN, TELLER.
- `POST /{consentId}/revoke {reason}` — ADMIN, TELLER. Revokes on the customer's behalf. Authorising stays the customer's own step.

**Response (`StaffConsentResponse`)**
- Fields: consentId, consentType (derived), customerId, customerName, tppClientId, tppName, scopes, status, expiryDate, createdAt, updatedAt, initiation, paymentId.
- Customer names are loaded with one `CustomerRepository.findAllById`. Not via a JOIN FETCH from another repository, because encrypted PII breaks across repository boundaries.
- TPP names come from `PartnerOrganizationRepository`.

**One revoke path: `ConsentService.revoke(consent, actor, reason)`**
- It writes the audit row and fires `CONSENT.REVOKED`.
- Used by the TPP/customer DELETE, the staff revoke, and `PartnerService.revokeConsentForOrg`. The partner revoke today skips both the audit and the webhook.

**Web (`features/open-banking/`)**
- Rewrite the service to use `api.getPage('/open-banking/consents', …)`, `get`, and `post …/revoke`.
- Align the model with the response.
- The list's filters are applied on the server.
- The detail page:
  - routes by `consentId`;
  - drops the Authorise button;
  - opens a Revoke modal that asks for a reason;
  - shows the initiation for PISP consents.
- Update the specs. Components stay `ChangeDetectionStrategy.Eager`.

### 23b — TPP registry screen on partner orgs (D7b)

**What was checked:** the screen calls `GET/POST /openbanking/tpp` and `?command=activate|revoke`. None of these exist. Its fields (`clientId`, `country`, `allowedScopes`, `certificateExpiry`) don't exist on any backend entity. A TPP in this system is a `PartnerOrganization`. It registers itself through the partner portal, so the bank never registers TPPs. `PartnerStatus.SUSPENDED` exists, but **nothing sets it and neither auth filter checks it**: `PartnerApiKeyAuthFilter` and `PartnerJwtFilter` admit any status.

**Backend:**
- `GET /api/v1/partners` (ADMIN, exists). `OrgResponse` gains:
  - `pendingApplication` (businessType, useCase, submittedAt), when the org is PENDING_REVIEW;
  - `activeConsents`: the count of AUTHORISED consents. It replaces the hard-coded `0`.
- New **`POST /api/v1/partners/{orgId}/suspend {reason}`** and **`POST /{orgId}/reactivate`** (ADMIN). Both are audited.
  - Suspend keeps the previous status (`status_before_suspension`, V65) so reactivate can restore SANDBOX or PRODUCTION.
  - Reactivating a non-suspended org returns `409 PARTNER_NOT_SUSPENDED`.
- **Suspension is enforced:**
  - `PartnerApiKeyAuthFilter` already JOIN FETCHes the org. A SUSPENDED org gets no authentication, so the call ends `401`.
  - `PartnerJwtFilter` reads the org's **current** status by the verified `orgId`, not the 24 h-old `status` claim; this is the cont. 43 tier rule.
  - The result: a suspended TPP can't call AISP, PISP, CBPII or the partner API. Its consents stay as they are, and work again on reactivation.
- `approve` / `reject` (exist) are offered only for PENDING_REVIEW.

**Web (`features/admin/open-banking.*`, `admin.service.ts`):**
- The model becomes the `OrgResponse` shape. Columns: name, status, tier, environment, active consents, created.
- Actions:
  - Approve / Reject for PENDING_REVIEW, with the application shown;
  - Suspend (reason modal) for SANDBOX / PRODUCTION;
  - Reactivate for SUSPENDED;
  - a "Consents" link to `/open-banking?tppClientId=<orgId>` (the 23 list, pre-filtered).
- The "Register TPP" modal and the invented fields are removed. A note says TPPs self-register in the partner portal.

**Tests:**
- `PartnerServiceTest`: suspend and reactivate, restoring the previous status, and the 409.
- Filter tests: a SUSPENDED org's API key gets 401, and so does a partner JWT whose claim still says PRODUCTION.
- Added to `OpenBankingPispIT` or `OpenBankingConsentIT`: suspend, then the TPP's AISP call → 401; reactivate → 200.
- Web specs for the screen and the service.

### 3 — ACCOUNT webhooks

**ACCESS_GRANTED (D3a)**
- In `authoriseConsent`, when the consent grants `accounts_read` or `card_read`, publish `ACCOUNT.ACCESS_GRANTED`.
- Payload: `{consentId, scopes, accounts:[{accountId, type, currency, maskedNumber}]}`.
- It lists bank accounts for `accounts_read`, and cards (via `CardServiceClient`) for `card_read`. Card lookup is fail-safe: if card-service is down, no cards are listed.
- Published through the same cross-bean `publishToPartner` as `CONSENT.AUTHORISED`.

**BALANCE_UPDATED (D2a)** is a coalescing pipeline. Every balance change writes a `Transaction` (12 call sites), so the hook goes there:
1. **`TransactionBalanceListener`**: a JPA entity listener on `Transaction` (`@PostPersist`); Spring Boot's `SpringBeanContainer` lets it be a bean. It publishes `AccountBalanceChangedEvent(accountId)`. Because it's on the entity, it also catches the batch `saveAll` in `InterestAccrualJob`.
2. **`AccountBalancePartnerNotifier`** (`@TransactionalEventListener(AFTER_COMMIT) @Async`, copying `PaymentReversalPartnerNotifier`):
   - Looks up the account's customer, then that customer's AUTHORISED, unexpired consents with `balances_read`, via a new `ConsentRepository` query on `customer_id` (already indexed).
   - Upserts a row into `partner_balance_notifications(org_id, account_id, consent_id, pending_since, last_sent_at)` (created in V65), keyed on `(org_id, account_id)`.
3. **`BalanceNotificationDispatcher`** (`@Scheduled`, every 15 s):
   - Picks rows that are pending and whose `last_sent_at` is older than the window (`app.partner.balance-webhook.window-seconds`, default 60).
   - Re-checks that the consent is still usable, publishes `ACCOUNT.BALANCE_UPDATED`, sets `last_sent_at`, and clears the pending flag.

Result: at most one event per TPP per account per window, and a change at the end of a window is never lost (trailing edge). State is in the database, not Redis, so it survives restarts.

Card balances (in card-service) are out of scope; this is documented.

### Tests (PR A)
- **`ConsentServiceTest`:**
  - each initiation validation 400;
  - `initiatePayment`: match, mismatch per field, already consumed, idempotent replay, currency, and a failed transfer that leaves the consent AUTHORISED;
  - CONSUMED in the `requireUsableConsent` matrix;
  - the ACCESS_GRANTED payload.
- **`PispControllerTest`:** updated for `initiatePayment`.
- **`StaffConsentControllerTest` / `StaffConsentServiceTest`:** filters, names, the shared revoke path, TELLER allowed, CUSTOMER refused.
- **`AccountBalancePartnerNotifierTest` + `BalanceNotificationDispatcherTest`:** coalescing, trailing edge, and a consent revoked before sending → nothing sent.
- **New `OpenBankingPispIT`** (`-Pfull-integration`; the first PISP integration test), with a real partner API key:
  - consent with initiation → authorise → pay → 201;
  - the same payment again → 200 with the same id and **one** transfer;
  - a different amount → 400;
  - **two concurrent payments → exactly one transfer** (proves the lock).
- **`OpenBankingConsentIT`:** staff list and revoke over HTTP; BALANCE_UPDATED reaches a stub webhook after a deposit.
- **Web specs:** service paths, list, detail.

---

## PR B — card FX (17), branch `feat/card-billing-currency`

### Rule
The "card amount" is the amount that touches the cardholder's money:
- **DE6 + DE51** when both are present and valid.
- **DE4 + DE49** when neither is present (unchanged).
- **Only one of DE6/DE51 → RC 30** (format error).

DE10 (conversion rate) is stored for audit but not recomputed: the scheme's DE6 is final.

**Accounting.** The bank's side of the transaction is in the billing currency, which is the account's currency, so it posts like any same-currency card movement. The scheme does the conversion, and the bank holds no foreign-currency position (IAS 21 §20–21: a transaction is recorded in the currency it is denominated in to the entity). No new GL lines. A bank-side foreign transaction fee would be a new item; it is not in this PR.

### fep-service
- `AuthorizationHandler` (0100) and `FinancialHandler` (0200) read DE6, DE51 and DE10.
- `ReversalHandler` (0400/0420) forwards DE6/DE51 when present.
- `AuthorizationRequest` gains `billingAmount`, `billingCurrencyCode` and `billingConversionRate`. `CardServiceClient` sends them in the authorize and reverse bodies.
- The mastercard, verve and afrigo packager XMLs gain DE10 (IFA_NUMERIC 8).
- `PackagerFieldSpecTest`: CANON gains DE6 and DE10, and its 0100 round-trip sets DE6, DE10 and DE51.

### card-service
- **`CardAuthRequest`** gains the three fields. It's a positional record, so the 12 test constructors and `FepAuthorizeContractTest` need updating.
- **`CardAuthorizationService.authorize`** resolves `cardAmountMinor` and `cardCurrency` once and uses them for:
  - the prepaid wallet compare and decrement;
  - the debit hold, the 0200 card-debit, and the failure-path release;
  - `quoteCharges`;
  - the fraud SINGLE_AMOUNT_LIMIT threshold (limits are in the card's currency). The duplicate check keeps DE4, which is the merchant-side amount;
  - the response balance currency. Today it reports DE49 although the balance is in the account currency.
- **The hold reference stays hashed on DE4/DE49.** The same message keeps the same key, and a resend with a different DE6 → RC 94, which is correct.
- **Migration V16 (`authorization_log`):** add `billing_amount NUMERIC(19,4)`, `billing_currency_code VARCHAR(3)` and `conversion_rate VARCHAR(8)`. `amount` / `currency_code` stay DE4/DE49 (used by analytics and the merchant view).
- **`SettlementService.addAuthorization`** stores the **card amount and currency** on the settlement item. Capture, batch totals, `gl_net`, refunds, interchange and scheme reports then follow automatically (D4a).
- **Reversal:**
  - The original's card amount is its billing amount, when set.
  - A partial 0400 that carries DE6 uses that amount.
  - Without DE6, the reversal is pro-rated: `billing × DE4_reversal / DE4_original`, rounded HALF_UP to minor units.
  - The full-vs-partial test compares amounts in the same currency.
- **Terminal simulator:**
  - `SimulateRequest` gains optional `billingAmount`, `billingCurrency` and `conversionRate`. They are validated as a set (`400 INVALID_BILLING_FIELDS`), following the cont. 54 `acquiringCountry` pattern.
  - `Iso8583Builder.formatAmount` uses the currency's exponent. Today it hard-codes ×100, which is wrong for JPY.
- **Web `TerminalSimulatorComponent`:** an optional "Cardholder billing" fieldset.

### Tests (PR B)
- **fep-service:** the handler tests forward DE6/DE51/DE10; `CardServiceClientTest` checks the body keys.
- **card-service `CardAuthorizationServiceTest`:**
  - EUR merchant on a USD account with DE6 → hold in USD → approved (the old `debitCurrencyMismatch` keeps DE6 absent);
  - DE6 without DE51 → RC 30;
  - prepaid wallet in the billing currency;
  - 0200 debit;
  - full and partial reversal (pro-rata, and with DE6);
  - the fraud threshold read in the card currency.
- **`FepAuthorizeContractTest`:** the new keys bind.
- **Clearing IT** (`CardClearingIT`-style): a foreign purchase clears at the USD amount.
- **Web:** simulator spec.

---

## PR C — reports sandbox (10), branch `feat/reports-sandbox`

### Database (migration V66, backend)

**Schema and views**
- `CREATE SCHEMA reporting`.
- Views list their columns explicitly: no `*`, and no secret, hash, token or PII columns. They cover:
  - loans, loan_repayment_schedule, loan_products, deposit_products;
  - accounts (without the account number), transactions, payments (without `beneficiary_iban`);
  - customers: `id`, `external_id`, `kyc_status`, `office_id`, `created_at` only (**no encrypted names**);
  - offices, tellers, teller_sessions, cash_transactions;
  - gl_accounts, journal_entries.
- **Never exposed:** platform_users, partner_*, hooks, audit_log, login_history, consents, 2FA/QR/push tokens, reports, and the Flyway, Quartz and Spring Batch tables.

**Role `cba_reporting`**
- Created `NOLOGIN` if missing (in a `DO` block).
- A member of no other role, so `set_config('role', …)` can't switch back to a more powerful role.
- `GRANT USAGE ON SCHEMA reporting` and `SELECT` on its views; nothing on `public`.
- `ALTER ROLE … SET default_transaction_read_only = on, statement_timeout = '30s', search_path = reporting, idle_in_transaction_session_timeout = '60s'`.
- The views are owned by the migration user, so they read the base tables with the owner's rights.

**The 7 core reports** are rewritten onto `reporting.*`:
- Placeholders become **bound parameters** (`${R_startDate}` → `:R_startDate`).
- Fix CustomerAcquisition's `COALESCE(::date…)` bug.
- Drop SavingsBalance's bogus `platform_users` join.

### Login and connection (no password in git)
- **`ReportingRoleInitializer`** runs at startup as the main user. When `REPORTING_DB_PASSWORD` is set:
  - it runs `ALTER ROLE cba_reporting LOGIN`;
  - it sets the password with pgjdbc's `PGConnection.alterUserPassword`, which computes the SCRAM verifier on the client, so the password never appears in SQL text or server logs.
  - It works on existing volumes and rotates the password on every restart.
- `REPORTING_DB_PASSWORD` unset → report runs answer `503 REPORTING_NOT_CONFIGURED` (fail closed). The dev and docker profiles get a dev default, as `DB_PASSWORD` does.
- **`ReportQueryRunner`** owns a private `HikariDataSource`:
  - It is not registered as a `DataSource` bean, so Boot's main DataSource, Flyway and Quartz are untouched.
  - Created lazily: same URL, user `cba_reporting`, at most 3 connections, `readOnly=true`.
  - Uses a `NamedParameterJdbcTemplate` with `queryTimeout` and `maxRows = cap + 1`.
  - The row cap is `app.reports.max-rows` (default 10 000). The response meta carries `truncated`.

### Service and API

**`ReportService.runReport`**
- Parameters are bound with types from `parameterType`: DATE → `LocalDate`, NUMBER → `BigDecimal`, BOOLEAN, STRING. This replaces the character filter in `sanitizeValue` and **closes today's parameter injection**.
- `validateSelectOnly` becomes `ReportSqlValidator` (D5a, JSqlParser). It requires exactly one statement, a `Select` (including `WITH`), no `INTO`, no data-modifying CTE, and no denied functions (`set_config`, `pg_*` admin/file functions, `dblink*`, `lo_*`, `current_setting`). SQL it can't parse → `400 REPORT_SQL_NOT_SUPPORTED`.
- Database errors → `400 REPORT_SQL_FAILED`, with the Postgres message and no stack trace.

**Reinstate `POST /api/v1/reports` (ADMIN)**
- Recover `ReportRequest` and `createReport` from commit `f6bc8db` (local reflog; also on `refs/pull/151/head`).
- Before saving, the SQL is validated **and dry-run** with `EXPLAIN` as `cba_reporting`.
- Reports are saved with `enabled=false` and `createdBy`.
- **New `POST /api/v1/reports/{id}/enable`** (ADMIN, not the creator → otherwise `403 SAME_USER`; D6a).
- Create, enable and run are all audited.

**Other changes**
- **CSV export:** cells starting with `=`, `+`, `-` or `@` get a leading `'`, to block spreadsheet formula injection now that output is user-controlled.
- **Infra:** `REPORTING_DB_PASSWORD` in docker-compose (with a dev default) and in the k8s `backend-secret` (`<CHANGE_ME>`); `REPORTING_DB_USERNAME` in the backend configmap.

### Web (`features/reports/`)
- A "New Report" modal: name, category, description, SQL, and parameters (name, label, type, required, default).
- An "Enable" button on disabled reports, and a "results truncated" banner.
- Export downloads through `HttpClient` as a blob. Today's `window.open` sends no bearer token, so it would fail once login is enforced.

### Tests (PR C)

**`ReportSandboxIT`** (real PostgreSQL; the test sets the role password). It proves the database boundary, not just the filter:
- **400** for each of:
  - `SELECT * FROM public.platform_users`
  - `customers.first_name_encrypted`
  - a multi-statement query
  - `DELETE`
  - `WITH x AS (DELETE …) SELECT`
  - `SELECT … INTO`
  - `set_config('role','cba_user',false)`
- `pg_sleep(40)` is stopped by the statement timeout at about 30 s (the timeout is lowered in the test profile).
- A row cap of 10 → `truncated`.
- A parameter value of `1) OR (1=1` is bound as text, not injected.
- **Each of the 7 core reports runs.** None has ever been run in a test.
- **Each guard is also tested with the parser switched off**, so the database role alone is shown to hold.

**Other backend tests**
- `ReportApiIT`: `createIsNotExposed` is replaced by: create (ADMIN) → saved disabled → enable by the same ADMIN → 403 → enable by another ADMIN → 200 → run. TELLER create → 403.
- `ReportServiceTest` and `ReportSqlValidatorTest`.
- `ReportExportServiceTest`: formula cells.

**Web specs**
- `reports-list.spec.ts`: the "no New Report" assertion becomes the create flow.
- The service spec, and blob export.

---

## Verification (each PR)
1. **Unit tests:** `cd backend && ./mvnw -o test`. For PR B, also `cd card-service && ./mvnw -o test` and `cd fep-service && ./mvnw -o test`. For all three PRs, `cd web && CI=true npx ng test --no-watch` and `npm run check:change-detection`.
2. **Full integration:** `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw clean verify -Pfull-integration` for each Java service touched. Regenerate the OpenAPI snapshots with `-Dupdate.api.snapshot=true`, then rerun without the flag. Report counts against the last runs: backend **985**, card-service **287**, fep-service **94**.
3. **PR CI:** the Backend, Card Service and Web CI gates, CodeQL, Gitleaks, and Dependency Review (PR C adds JSqlParser).

## Docs (each PR; the session gate is run and its output pasted)
- **`docs/api-reference.html` and the Postman collection** (plus their static copies):
  - A: the consent `initiation`, CONSUMED, the new 400/403 codes, the staff consent endpoints, the ACCOUNT.* payloads.
  - B: billing fields on authorize and in the simulator.
  - C: create/enable, typed parameters, `truncated`, the 503.
- **`partner-docs/docs/open-banking.md` and `webhooks.md`** (PR A).
- **`CLAUDE.md`:**
  - the Open Banking module note and the backend row (cont. 56–58);
  - the DE6/DE51 rule in the card section's "Multi-Currency Rules";
  - the sandbox in §15 Reports, and fix line 390, which overstates the parameter protection;
  - the component map (reports: create).
- **`cba-log.md`:** one entry per PR, including the gate grep.
- **`docs/deferred-backlog.md`:**
  - mark 24, 23 (incl. the TPP registry screen), 3, 17 and 10 ✅ DONE;
  - note that the item-10 code lived only in the reflog and PR refs, not on main.
- **`partner-docs`:** suspension (a suspended organisation's calls are refused, `401`) (PR A).
- **`docs/plans/2026-11-06-backlog-set-1.md`** plus a README row (committed in PR A; the row lists A, B and C as they merge).

## Not in this set
- Card balance changes triggering BALANCE_UPDATED: card-service has no consent index.
- A bank-side foreign transaction fee for card FX.
- Settlement currency DE5/DE50 (backlog 15d).
- A per-TPP aggregate cap on webhooks.
- Bank-side TPP registration (eIDAS/OBIE certificates, a directory check). TPPs self-register as partners.
