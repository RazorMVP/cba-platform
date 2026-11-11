# Backlog set 2: items 1, 15c, 4 (TLV), 15d (tolerance)

> Status: merged — PR A (1) #237 `1e5f4d6`, PR B (15c) #239 `9c84761`, PR C (15d tolerance) #238 `9bc9e31`, PR D (4 TLV) #236 `5a6630e` (2026-11-09 to 2026-11-11). Approved 2026-11-09 (user: "1 + 15c + 4 (TLV) + 15d (tolerance)"; every decision a).
> Set 1's plan is already in `docs/plans/2026-11-06-backlog-set-1.md`.

## Context

| # | Problem today | Outcome |
|---|---|---|
| 1 | An external payment (SWIFT/SEPA/ACH) is set to `COMPLETED` as soon as the gateway acknowledges it. It posts **no GL journal**, so GL 2001 drifts from the deposit sub-ledger. The debit skips the row lock and the hold check. A gateway timeout rolls back the debit even though the payment may have gone out. Reversing an external payment throws a NullPointerException (500). The network reference is lost when the caller sends their own reference | The payment goes to `PROCESSING` and posts to an in-transit liability. A signed callback from the gateway either settles it (paid out of the nostro) or returns it (credited back). Ops can resolve a stuck payment, giving a reason |
| 15c | Prepaid wallets can't be loaded (only seed data has a balance), and spend posts nothing to the GL. Prepaid spend appears in scheme files but is left out of the expected net. A partial reversal leaves the settlement item at its full amount | Wallets are loaded from and unloaded to the customer's own account. The wallet balance is a liability (GL 2500). Spend clears at batch close like debit purchases, earns interchange and enters the scheme reconciliation. An opening-balance scope covers existing wallets |
| 15d | A scheme settlement report must match card-service's figure to the cent, so a one-cent rounding difference blocks the date | Differences up to a tolerance configured per currency are recorded, with the difference going to a suspense account for ops to clear. Ops get a Scheme Reports tab to record reports |
| 4 (TLV) | The DE55 parser misreads 3-byte tags and padding. The 0110 echoes the card's whole DE55 plus a second `9F26` that carries the ARPC. The ARC is built from the DE38 approval code. Declines get no ARPC. The UnionPay QPBOC parser misreads long lengths | A real BER-TLV parser and builder. The response DE55 carries only tag `91` (ARPC ‖ ARC), with the ARC taken from DE39. Declines get an ARPC too. UnionPay uses the shared parser |

**Packaging:** four PRs. Each is merged when you say "merge N" and CI is green.

| PR | Items | Services | Migrations (numbered in merge order) |
|---|---|---|---|
| **A** | 1 | backend, web | backend V67 |
| **B** | 15c | card-service, backend, web | card-service V17, backend V68 |
| **C** | 15d | backend, card-service, web | backend V69, card-service V18 |
| **D** | 4 (TLV) | fep-service | none |

## Decisions for you
Reply "go" to take every recommendation, or name one to change it (e.g. "D3 b"). Accounting choices cite their source.

**D1 (item 1): what does an external payment post?**
- **a (recommended):**
  - At submit: DR customer deposit / CR new **2400 Outgoing Payments in Transit** (liability).
  - When settled: DR 2400 / CR nostro. The nostro is a new activity `ASSET_EXTERNAL_PAYMENT_NOSTRO`, mapped by default to the existing **1002 Cash at Bank**; you can remap it per correspondent.
  - When returned or failed: DR 2400 / CR customer deposit.
  - Why: debiting the customer doesn't extinguish the bank's obligation. It becomes an obligation to remit, which ends only when the bank pays out (IFRS 9 §3.3.1, B3.3.1(a); IAS 32 §11).
  - Fees (OUR/SHA charges) are out of scope.
  - A cross-currency external payment is refused: the source account must be in the payment currency, otherwise `400 CURRENCY_MISMATCH`.
- b: no in-transit account; post DR deposit / CR 1002 at submit. This treats the money as gone before it is.

**D2 (item 1): the gateway times out or answers 5xx, so we don't know whether the payment went out.**
- **a (recommended):** keep the debit, set `PROCESSING` with `submit_outcome=UNKNOWN`, and let the callback or ops settle it. The gateway got our `EXT-` reference as its idempotency key. Only a 4xx is a definite rejection, and that rolls back.
- b: roll back, as today. This risks money leaving the bank without a debit.

**D3 (item 1): a return arriving after the payment is `COMPLETED`.**
- **a (recommended):** not handled here. A return after settlement is an incoming credit, and incoming payments don't exist yet.
  - Callbacks are accepted only for `PROCESSING` payments. Anything else gets `409 PAYMENT_NOT_PROCESSING`.
  - A new status `RETURNED` separates a bank return from `FAILED` (rejected before settlement).
- b: also accept returns on `COMPLETED` payments (DR 1002 / CR deposit).

**D4 (item 1): manual resolution by ops.**
- **a (recommended):** `POST /api/v1/payments/{id}/external-outcome {outcome: SETTLED|RETURNED, reason}`.
  - ADMIN only, with a mandatory reason.
  - **Not the user who initiated the payment** (`403 SAME_USER`), the same rule as reports.
  - Audited, and it goes through the same path as the callback.
- b: callback only.

**D5 (15c): when does prepaid spend post to the GL?**
- **a (recommended):** at batch close, like a debit purchase.
  - Approval decrements the wallet, which acts as the hold. No journal.
  - Clearing posts DR **2500 Prepaid Card Liabilities** / CR 2300, plus interchange and fees exactly as in 15b. A refund of a cleared purchase posts the opposite, pro rata.
  - The bank's obligation to the cardholder moves to the scheme at clearing (IFRS 9 §3.3.1).
  - The wallet is a separate liability, not savings control. The CBN *Guidelines on Stored Value/Prepaid Card Issuance and Operations* (2010, revised 2012) describe the funds as on deposit with the issuer but not linked to a current or savings account.
  - 2012 revision §3.7 gives the holder a cash refund of the balance on request. That makes the wallet a financial liability (IAS 32 §11) at amortised cost (IFRS 9 §4.2.1).
  - No breakage income: IFRS 15 §5(c) excludes IFRS 9 instruments, so B44–B47 don't apply.
  - No fees are deducted from the balance; the CBN guidelines forbid it.
- b: post at approval (DR 2500 / CR 2300).

**D6 (15c): unload (redeeming the wallet to the customer's account).**
- **a (recommended):** include it. The CBN 2012 §3.7 refund right needs a path. It posts DR 2500 / CR deposit.
- b: loads only.

**D7 (15d): how is a difference within tolerance booked?**
- **a (recommended):** to a new **1004 Card Settlement Differences** suspense account. It allows manual journals, so ops can clear it.
  - The journal is DR 2300 at the expected net / CR 1003 at the reported net, with the difference on 1004. The difference is a claim on, or obligation to, the scheme pending investigation (IFRS 9 §3.1.1).
  - It is never netted into 2300 (IAS 1 §32).
  - If it proves unrecoverable, ops write it off to P&L by manual journal (IFRS 9 §5.4.4).
- b: write the difference straight to P&L (5006 / 4005) as immaterial (IAS 8 §8). This loses the investigation trail.
- **Tolerance:**
  - Set in `app.cards.settlement.difference-tolerance`: a map of currency → major units, with a `default`.
  - **Default 0**, so behaviour is unchanged until you set it.
  - It lives in the **backend**, which owns the GL. card-service sends both the expected and the reported net.
  - Above tolerance the backend refuses with `DIFFERENCE_OVER_TOLERANCE` and nothing is written, as today.

**D8 (item 4): what goes in the response DE55?**
- **a (recommended):** only tag `91` Issuer Authentication Data (EMV Book 3 Annex A).
  - Method 1 content: ARPC(8) ‖ ARC(2) (EMV Book 2 §8.2.1), with the ARC set to DE39's two characters.
  - Sent for approvals **and declines**, so the card can verify a decline.
  - No DE55 when there was no ARQC.
  - Issuer scripts (71/72) are not generated, though the builder supports them.
- b: keep echoing the request DE55 and replace the duplicate `9F26` with `91`.

---

## PR A — external payment lifecycle (item 1), branch `feat/external-payment-settlement`

**Migration V67**
- GL **2400 Outgoing Payments in Transit**: liability, parent 2000, id `30000000-…0015`, `manual_entries_allowed FALSE`. Inserted under the V63/V64 `WHERE EXISTS` guard.
- Activities `LIABILITY_EXTERNAL_PAYMENT_IN_TRANSIT` → 2400 and `ASSET_EXTERNAL_PAYMENT_NOSTRO` → 1002, as new enum constants in `FinancialActivityAccount`.
- `payments` gains:
  - `network_reference VARCHAR(64)`, unique where not null;
  - `submit_outcome VARCHAR(10)` (ACCEPTED/UNKNOWN);
  - `settled_at TIMESTAMPTZ`;
  - `outcome_source VARCHAR(10)` (CALLBACK/MANUAL).
- `payment_status` gains `RETURNED`. The column is VARCHAR with no CHECK, so only the enum changes.
- `external_payment_callbacks(id, reference, network_reference, outcome, reason, signature_ok, received_at, payment_id)`: an audit trail of every callback, rejected ones included.

**Gateway (`com.cba.payment.gateway`)**
- `GatewayResult.Status` gains `UNKNOWN`.
- `HttpExternalPaymentGateway`: 2xx → ACCEPTED, 4xx → REJECTED, 5xx or a transport error → UNKNOWN.
- `GatewayResult` gains `settled`:
  - `SimulatedExternalPaymentGateway` returns `accepted(ref, settled=true)`, so dev still completes in one call.
  - The HTTP gateway returns `settled=false`.

**`PaymentService.initiateExternalPayment` (rewritten)**
1. The source account's currency must equal `currencyCode`, otherwise `400 CURRENCY_MISMATCH`.
2. Debit with `AccountService.debitForSubledger` (row lock, holds, floor, lock-in, `Transaction` row) instead of the direct `source.debit`. Then post DR deposit / CR 2400 through a new `AccountGlPosting.postExternalPayment(change, phase)`, built from `balanceChangeLines` + `counterLine` like `postCashMovement`.
3. Submit to the gateway. This flips the order: the debit now comes before the submit, all in one transaction.
   - **REJECTED:** throw `EXTERNAL_PAYMENT_REJECTED`; everything rolls back.
   - **ACCEPTED and `settled`:** also post DR 2400 / CR nostro, status `COMPLETED`.
   - **ACCEPTED but not settled, or UNKNOWN:** status `PROCESSING`; store `network_reference` when given.
4. Publish `TransactionFraudEvent`, as transfers already do.

**New `ExternalPaymentOutcomeService.apply(reference|networkReference, outcome, reason, source, actor)`**
- Locks the payment (`findByReferenceForUpdate` / `findByNetworkReferenceForUpdate`). It must be an `EXTERNAL_PAYMENT` in `PROCESSING`, otherwise `409 PAYMENT_NOT_PROCESSING`.
- The same outcome sent again returns `200` with `repeat=true` (idempotent).
- **SETTLED:** DR 2400 / CR nostro; status `COMPLETED`, sets `settled_at`.
- **RETURNED / FAILED:** `creditForSubledger` back to the source, then DR 2400 / CR deposit; status `RETURNED` or `FAILED`, with `failureReason`.
- Audited as `EXTERNAL_PAYMENT_SETTLED|RETURNED|FAILED`.

**Callback: `POST /api/v1/payments/external/callbacks`**
- Body: `{reference, networkReference, status: SETTLED|RETURNED|REJECTED, reason}`.
- `permitAll` in `SecurityConfig` (placed before the POST catch-all) and excluded from `RateLimitFilter`.
- Authenticated by HMAC-SHA256 in the controller:
  - header `X-Gateway-Signature: sha256=<hex>` over `timestamp + "." + rawBody`;
  - `X-Gateway-Timestamp` must be within 5 minutes;
  - compared with `MessageDigest.isEqual` (constant time).
- Secret: `EXTERNAL_PAYMENT_CALLBACK_SECRET` (`app.payments.external.callback-secret`).
  - Unset → **`503 CALLBACK_NOT_CONFIGURED`** (fail closed).
  - Bad signature → `401 INVALID_SIGNATURE`, logged.
- Every callback is stored in `external_payment_callbacks`.

**Manual outcome (D4a):** `POST /api/v1/payments/{id}/external-outcome` (ADMIN) runs the same service. It refuses the initiator with `403 SAME_USER`, comparing through `AuditorAware` as `ReportService.enableReport` does.

**Fixes**
- `reversePayment` on an `EXTERNAL_PAYMENT` → `400 EXTERNAL_PAYMENT_NOT_REVERSIBLE` (it was a NullPointerException). Returns go through the outcome path.
- `GET /api/v1/payments/external?status=PROCESSING` filter for ops.

**Web (`features/operations/payments`)**
- `RETURNED` added to the `PaymentStatus` type, the filter and `statusVariant`.
- A PROCESSING external payment's detail page shows "Awaiting settlement" with Mark settled / Mark returned buttons (ADMIN), each with a reason modal.
- The Send Abroad success message says the payment is processing.

**Tests**
- `PaymentServiceTest`, external block: debit + 2400 journal; rejected → nothing; unknown → PROCESSING with the debit kept; simulated → COMPLETED with both journals; currency mismatch.
- `ExternalPaymentOutcomeServiceTest`: settle; return (credit back + journal); repeat; conflict; not processing.
- Callback controller tests: good signature, bad signature, stale timestamp, unset secret → 503.
- `HttpExternalPaymentGatewayTest`: 4xx versus 5xx.
- **New `ExternalPaymentIT`** (`-Pfull-integration`, HTTP gateway pointed at WireMock):
  - submit → PROCESSING, GL 2400 balanced;
  - signed callback SETTLED → COMPLETED, 1002 credited;
  - a second payment RETURNED → balance restored, 2400 back to zero;
  - unsigned callback → 401;
  - two concurrent callbacks → one journal;
  - the GL ↔ sub-ledger check holds.
- Web specs.

---

## PR B — prepaid wallets (15c), branch `feat/prepaid-wallet-gl`

**Backend (V68)**
- GL **2500 Prepaid Card Liabilities** (liability, id `…0016`) → `LIABILITY_PREPAID_CARD`.
- New table `prepaid_card_movements(reference UNIQUE, movement_type LOAD|UNLOAD|CLEARING|REFUND, customer_id, card_id, account_id, amount, currency_code, scheme, interchange_amount, interchange_direction, scheme_fee_amount, refunded_amount, journal_transaction_id, created_by, created_at)`.
- New `cardprepaid` package. **CARD_SERVICE only**: it isn't a manual journal path.
  - Every call is idempotent on `reference`: the same figures → repeat; different figures → `REFERENCE_CONFLICT`.
  - Each reference takes an advisory lock, as in `CardSchemeSettlementService`.

| Endpoint | Posts |
|---|---|
| `POST /api/v1/card-prepaid/loads {reference, customerId, cardId, sourceAccountId, amount, currencyCode}` | `debitForSubledger` (withdrawal rules). The account must belong to `customerId` (otherwise `404 ACCOUNT_NOT_FOUND`) and the currencies must match. **DR deposit / CR 2500** |
| `POST /api/v1/card-prepaid/unloads {…, destinationAccountId}` | `creditForSubledger`. **DR 2500 / CR deposit** |
| `POST /api/v1/card-prepaid/clearings {reference, cardId, amount, currencyCode, scheme, interchange…, schemeFeeAmount}` | **DR 2500 / CR 2300** plus the charge lines |
| `POST /api/v1/card-prepaid/clearings/{reference}/refund {refundReference, amount, currencyCode}` | The opposite of the clearing, with charges pro rata (HALF_UP). Refunded once, capped at the cleared amount |

- The 15b charge lines are refactored out of `AccountGlPosting.postCardSettlement` into `cardChargeLines(...)`, which the prepaid clearing also uses.
- New `TransactionType`s `PREPAID_CARD_LOAD` / `PREPAID_CARD_UNLOAD`, shown on the account statement.
- **Opening balances, scope `PREPAID`:**
  - `OpeningBalanceService` reads wallet totals per currency from card-service through `CardServiceClient` (new `GET /api/v1/cards/wallets/totals`, role CARD_READER).
  - It posts the difference from GL 2500 against 3900, once. The demo wallet (50,000 USD) enters the GL this way.
  - The GET doubles as the reconciliation of GL 2500 against the wallets.

**card-service (V17)**
- `wallet_movements(id, wallet_id, card_id, movement_type LOAD|UNLOAD, reference UNIQUE, account_id, amount, currency_code, status PENDING|COMPLETED|FAILED, failure_reason, next_retry_at, created_by, timestamps, version)`.
- New `WalletService`, using the claim-then-call pattern of `SchemeSettlementService`:

| Endpoint | Flow |
|---|---|
| `POST /api/v1/cards/{id}/wallet/loads {sourceAccountId, amount}` (ADMIN, TELLER) | 1. Transaction 1 inserts a PENDING movement.<br>2. The backend call runs outside any transaction.<br>3. Transaction 2 records the outcome:<br>• **COMPLETED**: wallet credited under `findByCardIdForUpdate`.<br>• **FAILED**: with the backend's reason.<br>• Backend unreachable: stays **PENDING** and the endpoint returns `202`. A `@Scheduled` poller retries with the same reference. |
| `POST /api/v1/cards/{id}/wallet/unloads {destinationAccountId, amount}` | Transaction 1 **decrements the wallet first**, so spend can't use money that is being unloaded. A refusal restores it; the poller retries an unknown outcome |
| `GET /api/v1/cards/{id}/wallet` | Balance plus the last 50 movements |
| `GET /api/v1/cards/wallets/totals` (CARD_READER) | Σ balance per currency |

- The wallet is created on its first load, in the source account's currency (issuing a card creates none today). An approval with no wallet now declines RC 51 (no funds) instead of 91.
- **Settlement (D5a)**
  - Prepaid approvals add a **PENDING** item: `movesGl()` now covers PREPAID, with the wallet-decrement key as the hold reference.
  - Batch close:
    1. Claims the item (CLEARING).
    2. Quotes charges. The `PREPAID` exclusion is removed from `InterchangeQualificationEngine`.
    3. Calls the clearing endpoint and sets `glNet`.

    Prepaid items then enter `findUnreportedCleared` and the scheme reports.
  - **Reversal before clearing:** the wallet is restored and `reducePendingItem` / `markReversed` run. This fixes the partial-reversal bug, where the item kept its full amount.
  - **Reversal after clearing:**
    1. The backend refund runs first. If the backend is unreachable → RC 96 and nothing is recorded, as for debit refunds.
    2. Then the wallet is restored and `refund_gl_net` is recorded.

**Web (card detail)**
- A PREPAID card's Overview gets a Wallet block: balance, Load and Unload modals, and a movements table.
- The account picker lists the card customer's ACTIVE accounts in the wallet currency.
- A PENDING movement is shown as "retrying".

**Tests**
- Backend:
  - `CardPrepaidServiceTest`: each endpoint, idempotency, ownership 404, currency, pro rata refund, refund cap.
  - New `CardPrepaidIT`: load → 2001 ↓ / 2500 ↑; clearing with interchange → 2300 / 4005; refund; unload; PREPAID opening balance.
- card-service:
  - `WalletServiceTest`: pending, retry, failed, unload restore.
  - `CardAuthorizationServiceTest`: the prepaid item is PENDING; a partial reversal reduces the item; a reversal after clearing refunds.
  - `SettlementServiceTest`: prepaid clears.
  - `InterchangeQualificationEngineTest`: prepaid earns interchange.
  - `CardClearingIntegrationTest`: load → spend → close → the scheme report includes prepaid.
- Web specs.

---

## PR C — scheme-report tolerance (15d), branch `feat/scheme-report-tolerance`

**Backend (V69)**
- GL **1004 Card Settlement Differences** (asset, id `…000e`, **`manual_entries_allowed TRUE`** so ops can clear it) → `ASSET_CARD_SETTLEMENT_DIFFERENCES`.
- `card_scheme_settlements` gains `expected_amount` and `difference`. The CHECK `amount <> 0` becomes "amount or expected is non-zero".
- `CardSchemeSettlementRequest` gains an optional `expectedAmount`. Absent means equal to the reported amount, which is today's behaviour.
- The tolerance comes from `app.cards.settlement.difference-tolerance` (default 0). Over tolerance → `DIFFERENCE_OVER_TOLERANCE`, returning expected, reported and difference.
- `postCardSchemeSettlement` posts:
  - the 2300 line at the expected net;
  - the 1003 line at the reported net;
  - the difference on 1004, on whichever side balances.

  Both signs of net are handled. A zero difference gives the old two-line journal.

**card-service (V18)**
- `scheme_settlement_reports` gains `difference`.
- `SchemeSettlementService.prepare` no longer refuses a mismatch itself. It sends `expectedAmount` + `amount`.
- A backend `DIFFERENCE_OVER_TOLERANCE` comes back as today's refusal (stamps removed, report deleted), with the figures, so the user sees the same detail as `AMOUNT_MISMATCH` gives today.
- A recorded report stores its difference.

**Web (settlement screen)**
- New **Scheme Reports** tab (none exists today):
  - expected-net lookup by scheme, date and currency;
  - a Record form;
  - a list showing expected, reported and difference, plus refusal detail.

**Tests**
- Backend: `CardSchemeSettlementIT` cases: within tolerance → three lines and a 1004 balance; over tolerance → refused, nothing posted; tolerance 0 → today's behaviour; a negative net with a difference.
- card-service:
  - `SchemeSettlementServiceTest`: the old mismatch test becomes "backend refuses over tolerance", plus "recorded with a difference".
  - `CardClearingIntegrationTest` with the stubbed backend.
- Web specs.

---

## PR D — EMV BER-TLV (item 4, TLV half), branch `feat/emv-ber-tlv`

**New `emv/tlv` package**
- `Tlv` record: tag hex, value, children, and `constructed()`.
- `BerTlvParser` (EMV Book 3 Annex B / ISO/IEC 8825-1):
  - multi-byte tags (bit 8 of each subsequent byte means "more follows");
  - lengths in short form, `81` and `82`; `83` and above are rejected as malformed;
  - `00`/`FF` padding between objects is skipped;
  - nested templates;
  - order and duplicates preserved in a list;
  - `TlvParseException` on truncation.
- `BerTlvBuilder`: encodes the tag and the minimal length form, with nesting (for 71/72 templates).

**Changes to existing code**
- `EmvDataParser` is rebuilt on `BerTlvParser`, keeping the `EmvData` API.
  - For duplicate tags the first occurrence wins and the rest are logged.
  - A malformed DE55 gives an empty `EmvData` plus a warning, as today, so the ARQC check fails closed.
- D8a:
  - `SchemeAdapter.embedArpc(response, arpc, arc)` writes DE55 = `91 0A <ARPC ‖ ARC>`, replacing the echoed request DE55. `appendArpcTag` is deleted.
  - `AuthorizationHandler` unsets field 55 on every response, then embeds the ARPC when an ARQC was present — for approvals **and declines**.
  - `ArpcGenerator.generate(arqc, arc)` takes the 2-byte ARC from DE39, not DE38.
  - `UnknownSchemeAdapter` stays a no-op.
- `UnionPaySchemeAdapter.parseQpbocTags` uses `BerTlvParser`, which fixes long lengths and the early stop, and now reads `9F79`. (`extractPrivateData` isn't called anywhere yet; wiring it in is out of scope.)
- New `EmvTag` constants: `ISSUER_AUTH_DATA "91"`, `ARC "8A"`, `ISSUER_SCRIPT_71/72`.

**Tests**
- `BerTlvParserTest` / `BerTlvBuilderTest`: 1-, 2- and 3-byte tags; `81`/`82` lengths with `83` rejected; padding; nested 70/77/71; duplicates; truncation; build → parse round trip.
- `EmvDataParserTest` extended.
- `ArpcGeneratorTest`: the ARC comes from the response code.
- New adapter tests (base, Visa, UnionPay): the response DE55 is exactly `91…`, with no `9F26` echo.
- `AuthorizationHandlerTest`: a chip approval and a chip decline both carry `91`; no DE55 when there was no ARQC.
- `FepSocketRoundTripTest`: a 0100 with real DE55 through the socket (real parser and generator); the 0110's DE55 parses to tag 91.

---

## Verification (each PR)
1. **Unit tests**, for each service the PR touches:
   - `cd backend && ./mvnw -o test`
   - `cd card-service && ./mvnw -o test`
   - `cd fep-service && ./mvnw -o test`
   - `cd web && CI=true npx ng test --no-watch && npm run check:change-detection`
2. **Full integration:** `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw clean verify -Pfull-integration` for each Java service touched.
   - Regenerate the OpenAPI snapshots with `-Dupdate.api.snapshot=true`, then rerun without it.
   - Baselines: backend **1072**, card-service **302**, fep-service **96**, web **1176**.
3. **CI gates:** Backend CI, Card Service CI, Web CI, CodeQL, Gitleaks and Dependency Review. fep-service has no required gate, so check its CI run by hand.

## Docs (each PR; the session gate is run and its output pasted)
- **`docs/api-reference.html` and the Postman collection**, plus their `docs-site/static` copies:
  - A: the callback and its signing, the outcome endpoint, PROCESSING/RETURNED, the new error codes.
  - B: the prepaid and wallet endpoints, and the PREPAID opening scope.
  - C: `expectedAmount` and `DIFFERENCE_OVER_TOLERANCE`.
  - D: no API change; write the proof line in `cba-log.md`.
- **`docs/card-api-reference.html`** (B, C).
- **`docs/integration-runbook.md`** (A): `EXTERNAL_PAYMENT_CALLBACK_SECRET` and the signing scheme for the payment provider.
- **`CLAUDE.md`:**
  - the backend, card-service and fep-service rows (cont. 59+);
  - §4 Payment Module: the lifecycle;
  - §14 GL: external payments and prepaid now post; GL 1004, 2400 and 2500;
  - the card multi-currency / 15c notes;
  - the fep-service EMV notes;
  - the component map.
- **`cba-log.md`:** one entry per PR, including the gate grep.
- **`docs/deferred-backlog.md`:**
  - Mark 1, 15c, 15d (tolerance) and 4 (TLV) done.
  - Item 4 keeps its HSM half open.
  - 15d keeps clearing-file intake and cross-border fees open.
- **Memory `project_gl_posting_decisions`:** record D1, D3 and D5–D7.
- **`docs/plans/2026-11-09-backlog-set-2.md`** plus a README row, committed in PR A.

## Not in this set
- External payment fees (OUR/SHA charges), cross-currency external payments, and incoming payments (which a return after settlement would need).
- Prepaid load fees, breakage, and the CBN balance limit (N250,000 cap). The limit can be added later as a load rule.
- Clearing-file intake and cross-border/ISA fees (the rest of 15d).
- HSM-based EMV key derivation (the first half of item 4), and wiring `extractPrivateData` into the authorize request.
