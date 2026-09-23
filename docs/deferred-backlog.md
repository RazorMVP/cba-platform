# Deferred Backlog — Known Future Work

> Durable record of features that are **intentionally deferred**, with enough context to pick up
> cold. Each was scoped and consciously left because it's a proper design task, a compliance
> decision, lacks a clean trigger, or is a documented dev-simplification — *not* because it was
> forgotten. First written Session 121 cont. 10 (items 1–3) after item C closed everything
> reasonably contained (CONSENT.EXPIRED, PAYMENT.REVERSED, AUTHORIZATION.REVERSED, RATE_LIMIT.*,
> FEP↔card-service contract). **Expanded cont. 11 (2026-07-21)** with a full-codebase sweep for
> every remaining deferral marker (items 4–7 + the roadmap section). **Items 8–9 added Session 125
> cont. 10 (2026-09-23)**: the CI secrets and variables that deployment and SonarCloud need.
>
> **Not in this list** (deliberately): intentional dev features that are correct as-is —
> `DevAuthBypassFilter` (prod uses Keycloak), fraud/3DS last-resort scalar guards, Stitch
> `*.prototype.html` design refs, demo-data plaintext markers. And the whole external-integration
> go-live set (HSM hardware, scheme network links, real vendor adapters) lives in
> `docs/integration-runbook.md` — see the Roadmap section at the bottom.

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

## 4. FEP EMV cryptogram + TLV handling is simplified for dev

**Effort: M · Risk: EMV correctness / crypto — real schemes reject malformed cryptograms & TLV**

### What it is
The fep-service EMV path validates/generates cryptograms and parses/builds DE55 (ICC data) with
**dev-simplified** algorithms and a hardcoded key, sufficient for the software HSM + tests but not
scheme-correct for production.

### Current state (fep-service)
- `emv/ArqcValidator.java:47` — "Uses a **hardcoded dev IMK**. Production retrieves the [issuer master key from the HSM]."
- `emv/ArpcGenerator.java:56,82` — "**simplified** MAC … in a real implementation the session key [derivation]"; "Simplified 3DES MAC for ARPC (dev mode)."
- `scheme/AbstractSchemeAdapter.java:46,62` — ARPC is appended "as raw bytes"; "**simplified append** — production code would use a proper **TLV builder**."
- `scheme/UnionPaySchemeAdapter.java:149` — "**simplified BER-TLV parser** targeting the CUP proprietary tags."

### What's needed
1. Real EMV session-key derivation and ARQC/ARPC via the **HSM** (the IMK never lives in code) — ties
   into the HSM go-live (`docs/integration-runbook.md`, Tier 1).
2. A proper **BER-TLV builder/parser** for DE55 construction and CUP/scheme private-tag parsing (this part
   is a code-correctness item independent of the HSM).

### Notes
`FepSocketRoundTripTest` / `PackagerFieldSpecTest` exercise ISO 8583 field *packaging*; the EMV *crypto* is
dev-simplified and not covered end-to-end. `ArqcValidatorTest` proves the dev derivation is self-consistent
(not a no-op), but it validates the dev IMK path, not production HSM derivation.

---

## 5. Card controls (contactless / CNP / international) are advisory, not enforced

**Effort: S–M · Risk: authorization decision path**

### What it is
`PUT /card-api/v1/cards/{id}/controls` only enforces **freeze** (→ card block/unblock). The
`contactless` / `cnp` / `international` toggles are **returned as-is and never enforced**.

### Current state (card-service)
- `openbanking/CardApiController.java:150` — "contactless / CNP / international flags are stored in
  card_limits / product features … **For now these are advisory flags returned as-is**; full
  implementation wires to card product config."

### What's needed
1. Persist the control flags (on `Card` or `card_limits` / product features).
2. Enforce them in `CardAuthorizationService.authorize` — decline CNP when disabled (DE22 entry mode /
   card-not-present), contactless when disabled (DE22 contactless), international when disabled (currency /
   country vs card home), with the right ISO response codes.
3. Fire the existing webhook events on control change.

### Seams / files
`card-service` `CardApiController` (controls endpoint), `CardAuthorizationService.authorize`, `Card` /
`CardLimit` (where flags live).

---

## 6. Backend rate-limit tier is always BASIC (no per-partner tier resolution)

**Effort: S–M · Risk: low**

### What it is
The **backend** `RateLimitFilter` (covering `/api/v1/**` + `/open-banking/v3.1/**`) applies **BASIC**
(100 rpm) to every caller unless an `X-Rate-Tier` header is present — it does not resolve the partner's
actual tier (PRO/ENTERPRISE) from their API key or JWT. (card-service's own filter *does* read
`api_keys.tier`; only the backend filter is behind.)

### Current state (backend)
- `config/RateLimitFilter.java:140` — "Partner Management … will inject a tier header after API key lookup.
  **For now all callers use BASIC.**"

### What's needed
Resolve the tier in the backend filter from the partner API key (`PartnerApiKey.tier`) or the JWT `tier`
claim — the exact request-side resolution `RateLimitEventNotifier` (cont. 9) already does for the orgId can
be reused (JWT claim / `PartnerApiKeys.hash` → key → tier). Then a PRO/ENTERPRISE partner gets its real limit.

### Seams / files
`config/RateLimitFilter.resolveTier`, `config/RateLimitEventNotifier` (reuse the org/key resolution),
`PartnerApiKey.tier`.

---

## 7. CDP file format, bureau transport, and encryption

**Effort: M · Risk: cardholder data leaving the platform — needs the bureau's actual spec before building**

> **Transport hardening (SFTP host-key pinning + HTTPS mTLS) is DONE — Session 125.**
> `SettlementFileTransmitter` now pins the scheme host key (`sftp-known-hosts-path` /
> `-entry`, `StrictHostKeyChecking=yes`) and fails closed when unpinned, and supports
> optional per-scheme mutual TLS via `SettlementTlsClientFactory`. Verified by
> `SettlementFileTransmitterSftpIntegrationTest` (an impostor host key is refused) and
> `SettlementFileTransmitterMtlsTest` (real handshake against a `needClientAuth` server).
> What remains below is the *bureau* side, which was mis-scoped as "Effort: S" alongside it.

### What it is
Card personalization data (CDP) is generated but **never serialised, encrypted, or transmitted**.

### Current state (card-service) — verified Session 125
- **`BureauFileTransport` does not exist.** It is named only in a comment at
  `bureau/BureauService.java:107` ("not implemented in dev stub"). `submitJob` generates a
  `CdpRecord`, stores its hash, sets `productionRequestDate`, and stops. Nothing turns CDP
  into bytes and nothing sends it anywhere — so there is no "before transmission" to encrypt at.
- **`panEncryptedForBureau` is mislabelled.** `CdpGenerator.java:101` sets it to
  `card.getPanEncrypted()` — the **Jasypt** ciphertext under *card-service's own* key — while the
  adjacent comment claims "bureau HSM decrypts using shared ZMK". A bureau cannot decrypt that;
  it has no access to our Jasypt key. Not a raw-PAN exposure (it is ciphertext), but the stated
  contract is wrong and will break on first real bureau onboarding.

### What's needed
1. The bureau's actual CDP file specification (Thales / Idemia / HID each publish their own).
2. CDP serialisation to that format.
3. Real key wrapping — PAN under a **shared ZMK** as the comment claims, or the bureau's public
   key — replacing the current Jasypt-ciphertext passthrough. Fix the misleading comment either way.
4. A `BureauFileTransport` (SFTP, reusing the now-pinned transmitter pattern).

### Why this is gated, not "cleanup"
An encryption scheme invented without the bureau's spec is likely to be rebuilt on onboarding,
and it cannot be meaningfully tested — there is no counterparty to decrypt it. Pick this up
when a named bureau and its spec exist.

---

## 8. Kubernetes deployment — cluster credentials not provisioned

**Effort: M · Risk: needs a cluster decision first, and the deploy jobs are broken in five
confirmed ways even once the secrets exist**

> Logged 2026-09-23 (Session 125 cont. 10) at the owner's request, right after images started
> building on main again for the first time since July 2026.

### What it is
`backend-ci.yml` and `card-service-ci.yml` each end in `deploy-staging` and `deploy-production`
jobs that `kubectl apply` the manifests in `infrastructure/k8s/`. **Neither has ever deployed
anything** — no cluster is wired to the repo.

### Current state — verified 2026-09-23
- **Images DO build and push.** First successful main run: `ad374f9`. Both
  `ghcr.io/razormvp/cba-platform/cba-backend` and `…/cba-card-service` are tagged `sha-ad374f9` + `main`.
- **Secrets missing:** `KUBE_CONFIG_PROD`, `KUBE_CONFIG_STAGING`. Each deploy job's first real step
  is `echo "${KUBE_CONFIG}" | base64 -d > /tmp/kubeconfig.yml`, which would write an empty file.
- **Variables missing:** `API_STAGING_URL`, `API_BASE_URL_STAGING`, `KEYCLOAK_URL_STAGING` — the repo
  has **no variables at all**. `API_STAGING_URL` is also the target of the weekly `zap-api-scan` in
  `security-scan.yml`, which therefore has nothing to scan.
- **GitHub environments:** `production` exists; **`staging` does not**. It would be auto-created on
  first use, but without the required-reviewers protection the CLAUDE.md setup checklist calls for.

### ⚠️ Five workflow bugs — fix these BEFORE adding credentials (all confirmed by reading the files)
1. **`deploy-production` can never run as written — skip propagation.** `api-doc-check` is
   `if: github.event_name == 'pull_request'`, so on a push to main it is *skipped*. `docker` still
   runs because its `if` starts with `always()`, but `deploy-production` (`needs: docker`,
   `if: github.ref == 'refs/heads/main'`) has no status function, so its implicit `success()` sees
   the skipped job further up the chain. Observed on run `35896354660`: docker `success`,
   deploy-production `skipped`. Fix: `if: always() && needs.docker.result == 'success' && github.ref == 'refs/heads/main'`,
   and the same shape for `deploy-staging` with `refs/heads/develop`.
2. **Wrong file path.** The rewrite step edits `infrastructure/k8s/backend/deployment.yaml` and
   `infrastructure/k8s/card-service/deployment.yaml`. Those files **do not exist** — they are
   `backend-deployment.yaml` and `card-service-deployment.yaml`. `sed -i` would exit non-zero with
   "No such file or directory" and fail the job.
3. **Wrong search pattern.** It replaces `cba/backend:latest` / `cba/card-service:latest`, but the
   manifests contain `ghcr.io/razormvp/cba-platform/cba-backend:latest` (and the card-service
   equivalent). Even with the path fixed, **nothing would be replaced** and the rollout would
   silently keep `:latest`.
4. **Wrong tag.** It writes `sha-` + the **full 40-char** SHA (`${{ github.sha }}` in backend,
   `$(git rev-parse HEAD)` in card-service), but `docker/metadata-action` pushes the **7-char short**
   SHA (`sha-ad374f9`). The rewritten image reference would not exist in GHCR → `ImagePullBackOff`.
   Simplest fix: read the tag from the `docker` job's `outputs.tags` (card-service already exposes
   `needs.docker.outputs.tags` as `IMAGE_TAGS` but never uses it).
5. **Mixed-case registry path.** Both build the reference from `${{ github.repository }}`, which is
   `RazorMVP/cba-platform` — not a valid lowercase OCI reference. This is the GHCR lowercase gotcha
   already in CLAUDE.md; lowercase it (`${GITHUB_REPOSITORY,,}`) or reuse the metadata-action output.

### What's needed
1. **Decide the target cluster** (managed EKS/GKE/AKS or other) for staging and production.
2. Fix the five bugs above.
3. Create each kubeconfig with a **namespace-scoped service account** limited to `cba-platform`,
   not a cluster-admin credential. Add as secrets: `base64 < kubeconfig` → `KUBE_CONFIG_STAGING` /
   `KUBE_CONFIG_PROD`.
4. Set variables `API_STAGING_URL`, `API_BASE_URL_STAGING`, `KEYCLOAK_URL_STAGING`.
5. Create the `staging` environment and add **required reviewers** to `production`.
6. Replace every `<CHANGE_ME>` in `infrastructure/k8s/**` secrets via Sealed Secrets or Vault, per
   CLAUDE.md — **never real values in git; this repo is public**.
7. Deploy to staging first and confirm `kubectl rollout status` completes and the pods run the
   `sha-<short>` image, not `:latest`.

### Seams / files
`.github/workflows/backend-ci.yml` + `card-service-ci.yml` (`deploy-staging`, `deploy-production`),
`infrastructure/k8s/backend/backend-deployment.yaml`, `infrastructure/k8s/card-service/card-service-deployment.yaml`,
repo Settings → Secrets / Variables / Environments.

---

## 9. SonarCloud — token and organisation not provisioned

**Effort: S · Risk: none to the product; the job is already non-blocking**

> Logged 2026-09-23 (Session 125 cont. 10) at the owner's request.

### What it is
`backend-ci.yml`'s `sonar` job fails on every run in ~15 s. It is `continue-on-error: true`, so it
blocks nothing — but it is permanent red noise on every backend PR and main push, and **the 70%
coverage quality gate CLAUDE.md describes is not enforced by anything**.

### Current state — verified 2026-09-23
- **Secret missing:** `SONAR_TOKEN`.
- **Variable missing:** `SONAR_ORG` (passed as `-Dsonar.organization`). Provisioning only the token
  would still fail — **both are required**.
- The job runs `mvn verify sonar:sonar` with project key `cba-platform_backend`; that project must
  exist in SonarCloud under the chosen organisation.

### What's needed
1. Create (or pick) a SonarCloud organisation bound to the `RazorMVP` GitHub account.
2. Create the project with key **`cba-platform_backend`**, or change the key in the workflow to match.
3. Generate a token (SonarCloud → My Account → Security) → repo secret **`SONAR_TOKEN`**.
4. Set repo variable **`SONAR_ORG`** to the organisation slug.
5. Re-run the backend workflow and confirm the job goes green.
6. **Then decide** whether the quality gate should block. With `continue-on-error: true`, even a
   failed gate passes CI — removing it is a policy call.

### Seams / files
`.github/workflows/backend-ci.yml` (`sonar` job), repo Settings → Secrets / Variables.

---

## Roadmap-level items (tracked elsewhere — listed here for completeness)

- **Mobile — Flutter Phase 3**: not started; the `mobile/` dir is empty. Backend is ready (push registry,
  `/api/v1/self/*`, `cba-mobile` Keycloak client). Full status in **CLAUDE.md → "Mobile Frontend … NOT YET BUILT"**.
- **External-integration go-live** (credential/vendor-gated): HSM (Thales payShield), card-scheme ISO 8583
  network links + certification, 3-D Secure Directory Server registration, card personalization bureau,
  settlement SFTP/HTTPS credentials, and the real per-vendor adapters for SMS / credit-bureau / external-payment
  / push. All catalogued with env vars + go-live steps in **`docs/integration-runbook.md`**.

---

## Cross-references
- Event mechanism + already-wired events: see **CLAUDE.md → Partner Module → "Partner/BaaS Hardening"**.
- External-integration credential/go-live steps: `docs/integration-runbook.md`.
- Full partner webhook event catalogue (17 events): CLAUDE.md Partner Module.
