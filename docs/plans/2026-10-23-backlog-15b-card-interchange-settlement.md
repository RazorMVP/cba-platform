> **Status:** open — PR pending. Approved 2026-10-24 in plan mode (user: "1a 2a 3a 4a 5a"). Body verbatim.

# Backlog 15b — card interchange income, scheme fees and the scheme cash leg


> **Decisions (user, 2026-10-23: "1a 2a 3a 4a 5a"):**
> - **D1a gross:** interchange earned → new GL **4005 Card Interchange Income**; scheme fees → **5006 Card Scheme Fees**;
>   interchange the bank *pays* on ATM cash → **5007 Card Interchange Expense** (IFRS 15 B35/B37 — the bank is principal
>   for the card service; the scheme is a supplier, not a customer, so §70 doesn't apply; IAS 1 §32 no netting).
> - **D2a at clearing, in the capture's journal:** DR 2300 / CR 4005, DR 5006 / CR 2300; ATM 0200 at approval with
>   card-debits (DR 5007 / CR 2300); refund of a cleared debit reverses them pro rata (IFRS 15 §38 point in time).
> - **D3a** 2300 = one net payable per scheme; card-service tracks expected net per (scheme, date, currency) (IAS 32 §42).
> - **D4a** ops record the scheme's settlement report; equal to expected → DR 2300 / CR new **1003 Card Scheme Settlement
>   Account**, items + batch SETTLED; different → refused with the difference, nothing posted.
> - **D5a** scope = D1–D4 + interchange engine fixes. New **15d**: clearing-file intake (amount differences, incremental
>   presentments), cross-border/ISA fees, RC 91 reconciliation view, mismatch tolerance. New item: export-file bugs.
> Save to memory `project_gl_posting_decisions` at implementation start.

## Context
15a made clearing debit the customer (DR deposit / CR 2300) but interchange, scheme fees and the cash paid to the
scheme still post nothing, and 2300 never clears. Exploration found: interchange is computed only by an ADMIN
diagnostic, in the **acquirer** direction (`net = gross − interchange − fees`); scheme fees of every type are applied
to every transaction; fixed fees mix major/minor units; `UNION_PAY` (card-service) vs `UNIONPAY` (fep) means UnionPay
never matches a rate; no batch ever reaches SETTLED; the backend has no income/expense/settlement-bank account for
cards and no journal endpoint card-service can call; journals have no idempotency (15a's is the hold row).

## Conventions
- Backend ↔ card-service amounts are **major units** of the account currency (= DE49; backend refuses a mismatch).
  card-service `settlement_items` stay minor units. Figures rounded **once** in card-service (currency digits, HALF_UP);
  backend refuses a larger scale.
- `fixed_fee` = major units of the row's `currency_code`; applied only when it equals the transaction currency
  (no FX in card-service) — else skipped with a WARN. Fees applied: **ASSESSMENT + NETWORK only**.
- Direction: DE3 `00` purchase → EARNED; `01` cash → PAID; `20` refund → none; PREPAID → none (15c, no GL).
  No rate → zero interchange, WARN, fees still apply; engine never throws into authorization/clearing.
- `UNIONPAY` canonical (`Schemes.normalize`).
- 2300 sign (+ = bank owes scheme): clearing `gl_net = gross − earnedIC + paidIC + fees`; refund
  `refund_gl_net = −refund + earnedICrev − paidICrev − feeRev`.
- Worked example (USD): purchase 100.00, IC 1.50%, fees 0.11%+0.0195 → DR dep 100 / CR 2300 100; DR 2300 1.50 / CR 4005;
  DR 5006 0.13 / CR 2300 → +98.63. ATM 50.00, IC paid 0.50, fees 0.07 → +50.57. Expected net **149.20** → DR 2300 /
  CR 1003 149.20. Later partial refund 40.00 → DR 2300 40 / CR dep; DR 4005 0.60 / CR 2300; DR 2300 0.05 / CR 5006 → −39.45
  counted in the refund day's expected net.

## Backend
- **`V64__card_interchange_settlement.sql`** (V63 pattern, `manual_entries_allowed=FALSE`): GL `…00d` 1003 (ASSET),
  `…035` 4005 (INCOME), `…046` 5006, `…047` 5007 (EXPENSE) + mappings `ASSET_CARD_SCHEME_SETTLEMENT`,
  `INCOME_CARD_INTERCHANGE`, `EXPENSE_CARD_SCHEME_FEES`, `EXPENSE_CARD_INTERCHANGE`; `account_holds` + `card_scheme`,
  `interchange_amount`, `interchange_direction` (CHECK EARNED/PAID), `scheme_fee_amount`, `interchange_reversed`,
  `scheme_fee_reversed`; table `card_scheme_settlements(reference UNIQUE, scheme, settlement_date, currency_code,
  amount ≠ 0, journal_transaction_id, created_by, created_at)`.
- `FinancialActivity` + 4 values; `AccountHold` + fields; enum `CardInterchangeDirection`; record `CardCharges`.
- DTOs (all optional — 15a callers unchanged): `CardCaptureRequest`, `CardDebitRequest` + `interchangeAmount`,
  `interchangeDirection`, `schemeFeeAmount`, `scheme`; `CardHoldResult` + posted/reversed figures (factory to keep call
  sites readable).
- `AccountService`: `validateCharges` → decline `INVALID_CARD_FEES` (negative, scale > currency digits, missing
  direction, EARNED > amount) — a decline result, never a 400 (card-service treats 4xx as unreachable). Capture/debit
  store the figures; an idempotent repeat returns the **stored** figures (different figures → WARN only; different
  amount still `REFERENCE_CONFLICT`). `refundCardDebit` reverses `figure × refund/captured` (HALF_UP; full refund =
  exact) — a captured hold refunds only once (15a), so no remainder tracking.
- `AccountGlPosting.postCardSettlement(account, before, CardCharges, reversal, …)`: 2300 fee lines first, then P&L via
  `addProfitOrLoss` (foreign currency through the FX position, IAS 21 §21); resolve an activity only when its amount > 0
  (a plain 15a capture needs no new mapping). New `postCardSchemeSettlement(amount signed, ccy, …)`: + → DR 2300 /
  CR 1003, − → DR 1003 / CR 2300.
- New package `com.cba.cardsettlement`: **`POST /api/v1/card-settlements`** (CARD_SERVICE/ADMIN) `{reference, scheme,
  settlementDate, currencyCode, amount}` → insert-first on unique reference; repeat with same figures = no second
  journal (`repeat=true`), different = `REFERENCE_CONFLICT`; journal dated today (closed periods can't block), EntityType
  CARD. `SecurityConfig` POST matcher before the catch-all. OpenAPI snapshot regenerated.

## card-service
- **`V13__interchange_scheme_settlement.sql`**: UNION_PAY → UNIONPAY (CHECKs + data in rates, fees, authorization_log);
  `scheme_fees.currency_code` (VERVE/AFRIGO → NGN), fixed-fee comment; `interchange_log` + `gross_amount`,
  `currency_code`, `interchange_direction`, `settlement_item_id`; `settlement_items` + `scheme`, `interchange_amount`,
  `interchange_direction`, `scheme_fee_amount`, `gl_net`, `refunded_amount`, `interchange_reversed`,
  `scheme_fee_reversed`, `refund_gl_net`, `refund_settlement_date`, `settlement_report_id`, `refund_report_id`; table
  `scheme_settlement_reports(scheme, settlement_date, currency_code, reported_net, expected_net, item_count,
  refund_count, status POSTING|RECORDED, backend_reference UNIQUE, journal_transaction_id, …, UNIQUE(scheme, date,
  currency))`; backfill 15a items (`scheme`, `gl_net = amount` for cleared debits, refund columns for REVERSED).
- **Engine** `InterchangeQualificationEngine.quote(scheme, cardType, processingCode, entryMode, mcc, grossMajor, ccy)` →
  `IssuerCharges(scheme, interchange, direction, schemeFee, rateApplied)`; `record(...)` writes `interchange_log`;
  `calculate()` (diagnostic) rebuilt on `quote` — net becomes "issuer owes scheme". `Schemes.normalize`;
  `SchemeFeeRequest` + `currencyCode`.
- **Clearing** (`SettlementService`): `claim()` quotes and persists figures on the item (retry reuses them);
  `clear()` sends them in the capture body (`BackendClearingClient.capture(..., IssuerCharges)`); `record()` adopts the
  backend's returned figures and sets `gl_net`; `addAuthorization(..., figures)` for 0200; `recordRefund(authLogId,
  refundMinor, figures)` under the item lock.
- **Authorization** (`CardAuthorizationService`): 0200 DEBIT quotes and sends figures with card-debits; `reverse()`
  records refund figures when the backend refunded (`releaseDebitHold` returns the hold); scheme stored normalized.
- **Scheme report** (`SchemeSettlementService`; ADMIN on `SettlementController`):
  `POST /api/v1/cards/settlement/scheme-reports {scheme, settlementDate, currency, netAmount}`,
  `GET …/scheme-reports?date=`, `GET …/scheme-reports/expected?scheme=&date=&currency=`.
  Refuses `DATE_NOT_CLOSED` (date ≥ today), `BATCH_NOT_CLOSED` (OPEN batch or PENDING/CLEARING items).
  Expected = Σ `gl_net` of unreported items + Σ `refund_gl_net` dated that day, per currency. Mismatch →
  `AMOUNT_MISMATCH` with expected/reported/difference, **nothing written**. Match → row POSTING + items stamped, backend
  call **outside** a transaction, then RECORDED + `markBatchesSettled(date)`; backend refused → row deleted, stamps
  removed; unreachable → stays POSTING, a retry finishes. Same report again → `repeat`, no call; zero expected → RECORDED
  with no backend call. Batch → SETTLED when every GL item of the date is in a RECORDED report.

## Docs
api-reference + static, card-api-reference + static, Postman (+ static; "Card scheme settlement" folder), CLAUDE.md
(GL list, sign convention, V64/V13, versions), cba-log, deferred-backlog (15b done; new 15d; new export-bugs item;
prepaid gets no interchange until 15c), memory, plan copy `docs/plans/2026-10-23-backlog-15b-card-interchange-settlement.md`
(+ mark 15a plan merged `6bcd9d4`).

## Verification
- **Backend `CardClearingIT`**: capture with fees posts gross lines + trial balance balanced; repeat returns stored
  figures; invalid fees declined; no fees = 15a behaviour even with new mappings removed; unmapped fee activity rejects;
  EUR account with functional NGN → P&L via FX position; ATM paid interchange; full refund nets to zero; partial 40/100
  pro rata; refund repeat; legacy null-fee hold. **`CardSchemeSettlementIT`**: positive / negative net, same reference
  twice = one journal, different amount = conflict, concurrent same reference = one journal, roles, unmapped 1003,
  closed period. `-Pfull-integration` all green + snapshot.
- **card-service**: engine (issuer direction, cash paid, refund/prepaid none, fixed fee units, JPY rounding, fee types,
  UnionPay spellings, no rate, repository failure); clearing (fees in capture, retry reuses, backend figures adopted,
  refund figures); scheme report (match → SETTLED, mismatch writes nothing, repeat, conflict, open batch, today, backend
  refused, unreachable then retry, refunds on refund date, failed/reversed excluded, negative, zero, multi-currency);
  0200 figures; WireMock IT (capture body carries fees, report posts once, mismatch never calls backend, batch SETTLED).
  `-Pfull-integration` all green + snapshot.
- Gate grep: new endpoints → api-reference/Postman/card-api-reference entries.
