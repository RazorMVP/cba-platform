# Deferred Backlog — Known Future Work

> Durable record of features that are **intentionally deferred**, with enough context to pick up
> cold. Each was scoped and consciously left because it's a proper design task, a compliance
> decision, or lacks a clean trigger — *not* because it was forgotten. Updated Session 121 cont. 10
> (2026-07-21) after item C ("small deferred domain features") closed everything reasonably
> contained (CONSENT.EXPIRED, PAYMENT.REVERSED, AUTHORIZATION.REVERSED, RATE_LIMIT.*, and the
> FEP↔card-service contract).

Legend — **Effort**: S / M / L. **Risk**: what makes it non-trivial.

---

## 1. Async external-payment settlement lifecycle

**Effort: L · Risk: money path + external contract**

### What it is
External payments (SWIFT/SEPA/ACH) currently settle **synchronously**: `PaymentService.initiateExternalPayment`
submits to the pluggable `ExternalPaymentGateway`, and on an ACCEPTED ack sets the `Payment` status to
`COMPLETED` immediately. Real cross-border/interbank settlement is **asynchronous** — a submit is only an
acknowledgement; final settlement (or a return) arrives later, out of band.

### Current state (as built, Session 121 cont. 0)
- `com.cba.payment.gateway.ExternalPaymentGateway` — `submit(instruction)` → `GatewayResult` (ACCEPTED / REJECTED).
- `SimulatedExternalPaymentGateway` (default): accepts + returns a synthetic `networkReference`, settles now.
- `HttpExternalPaymentGateway` (`app.payments.external.gateway=HTTP`): real POST; ACCEPTED → treated as final.
- `PaymentService.initiateExternalPayment`: **submits BEFORE debiting** (a REJECT rolls back — no phantom debit),
  then sets `COMPLETED`. Stores `networkReference` in `externalReference` when the caller gave none.
- `PaymentStatus` enum already has `PENDING, PROCESSING, COMPLETED, FAILED, REVERSED` (no `RETURNED`).

### What's needed
1. On ACCEPTED, set status **`PROCESSING`** (not `COMPLETED`) for the HTTP gateway path. (Simulated gateway may
   still settle immediately for dev.)
2. A **status-callback receiver** the PSP/gateway calls with the final outcome, keyed on `networkReference`:
   e.g. `POST /api/v1/payments/external/callbacks` with `{networkReference, status, reason}`.
   - **Idempotent** (same callback may arrive twice) + **authenticated** (HMAC signature / shared secret /
     mTLS — do not trust an unauthenticated status flip on the money path).
3. Transition on callback: `PROCESSING → COMPLETED` or `→ FAILED` (add a `RETURNED` status if you want to
   distinguish a bank return from an outright failure).
4. On FAILED/RETURNED: **credit the source back** (reverse the debit) — mirror the rollback semantics already
   in `reversePayment`.
5. Move the partner webhook firing to the **terminal** callback: today `PispController` fires
   PAYMENT.COMPLETED/FAILED off the *synchronous* response; with async, PAYMENT.INITIATED fires on submit and
   the terminal event fires from the callback handler.

### Seams / files
`com.cba.payment.gateway.*`, `PaymentService.initiateExternalPayment`, `Payment` (status), a new callback
controller, `PispController` event firing, `PaymentReversedEvent`/reversal for the return path.

### Gotchas
- Money path — every state transition needs tests + audit.
- The callback must be idempotent and signed; a spoofed "COMPLETED" must not be possible.
- Reconciliation: payments stuck in `PROCESSING` past an SLA need a sweep/timeout (a `@Scheduled` job).

---

## 2. Full-PAN decrypt in card settlement export

**Effort: M · Risk: PCI-DSS scope — requires compliance sign-off before building**

### What it is
Scheme settlement files (Visa BASE II, Mastercard IPM, NIBSS, PAPSS, CUPS) generally require the **full PAN**.
The export currently emits **masked PAN only** (first6 + mask + last4); the full-PAN field is empty by design.

### Current state (card-service)
- `SettlementFileExportService.buildExportRecords` uses a **pure JdbcTemplate SQL** path.
  `card-service/.../settlement/SettlementFileExportService.java:279` →
  `"",  // pan — masked-only; full PAN decrypt deferred (Gap 7 decision)`.
- The PAN is stored **encrypted** (`cards.pan_encrypted`, Jasypt/`FieldEncryptor`) — **cannot be decrypted in SQL**.
- The 5 exporters already carry a `pan` field on `SettlementExportRecord`; they'd receive the real value.

### What's needed
1. Decrypt the PAN per record. The SQL path can't; options:
   - (a) Load each card via **JPA** (`FieldEncryptor` decrypts on read) for the PAN field, keeping the rest of
     the join in SQL; or
   - (b) A dedicated decrypt step in the serializer that resolves `card_id → full PAN` via the card repository.
2. Keep the masked PAN for logs/audit; only the **file body** gets the full PAN.

### ⚠️ Why this is gated, not "cleanup"
Putting full PANs into generated files **widens PCI-DSS scope** (storage + transmission of cardholder data) —
the masking was a deliberate control. Do **not** implement this as a silent change. It needs:
- An explicit compliance decision (scope, retention, encryption-at-rest of the export files, secure transmission).
- The settlement files themselves treated as CHD: encrypted at rest, access-controlled, short retention.

### Seams / files
`card-service` `SettlementFileExportService.buildExportRecords` (line ~279), `SettlementExportRecord`,
`Card.panEncrypted` / `FieldEncryptor`, the 5 `*Exporter` classes.

---

## 3. ACCOUNT.ACCESS_GRANTED / ACCOUNT.BALANCE_UPDATED partner webhooks

**Effort: ACCESS_GRANTED S–M · BALANCE_UPDATED M–L · Risk: event volume + missing linkage**

### What it is
Two of the 17 partner webhook events never fire — there's no clean domain trigger for either.

### ACCOUNT.ACCESS_GRANTED
- **Candidate trigger:** AISP consent authorization. When an `accounts`-scoped consent is **authorised**, the
  TPP now has account access → fire ACCESS_GRANTED (per granted account, or once with the account list).
- **Seam:** `ConsentService.authoriseConsent` (already fires CONSENT.AUTHORISED; add ACCESS_GRANTED for
  account-scoped consents, resolving the accounts in scope).
- **Open question:** semantics vs CONSENT.AUTHORISED — is it per-account or per-consent? Decide before building.

### ACCOUNT.BALANCE_UPDATED
- **Candidate trigger:** any balance change (deposit / withdrawal / transfer / interest posting) on an account
  a TPP has an active consent for.
- **Why it's hard (the real blocker):**
  1. **No account → consenting-orgs lookup.** Consents are keyed by customer/TPP, not indexed by account. Need a
     query "which partner orgs have an active AISP consent covering account X."
  2. **Balance changes happen in many places** — `PaymentService` (transfer/external/reversal), `AccountService`
     (deposit/withdraw/hold), interest-posting CoB, teller cash. No single choke point. Would need a domain
     event on balance change (e.g. published from `Account` mutation or `Transaction` persist) + a listener.
  3. **High volume** — every transaction on a consented account fans out a webhook. Needs throttling/coalescing
     (e.g. debounce per account per window) to avoid a storm, plus the RATE_LIMIT-style dedup.
- **Seams:** a new balance-change domain event + `@TransactionalEventListener(AFTER_COMMIT)` listener (mirror
  `PaymentReversalPartnerNotifier`), a consent-by-account query, `PartnerWebhookDeliveryService`.

### Gotchas
- BALANCE_UPDATED is effectively a fan-out subscription system — treat it as such (volume, dedup, ordering),
  not a one-line wire. This is why it was deferred.

---

## Cross-references
- Event mechanism + already-wired events: see **CLAUDE.md → Partner Module → "Partner/BaaS Hardening"**.
- External-integration credential/go-live steps: `docs/integration-runbook.md`.
- Full partner webhook event catalogue (17 events): CLAUDE.md Partner Module.
