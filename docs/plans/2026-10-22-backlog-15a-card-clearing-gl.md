> **Status:** open — PR pending. Approved 2026-10-22 in plan mode (user: "1a 2a 3a 4a"). Body verbatim.

# Backlog 15a — card clearing posts to the GL (capture holds, complete 0200, sweep expired holds)

> **Decisions (user, 2026-10-22: "1a 2a 3a 4a"):**
> - **D1a** clearing = end-of-day batch close presents each approved, unreversed purchase at its authorized amount
>   (no scheme clearing-file intake yet).
> - **D2a** 0200 (ATM cash) debits immediately at approval; a 0400 refunds it.
> - **D3a** split: **15a** (this PR) capture/GL/0200/sweep/batch wiring; **15b** interchange income + scheme fees +
>   cash leg (needs an IFRS 15 B34–B38 decision); **15c** prepaid funding + liability GL.
> - **D4a** late presentment (hold already expired) debits under normal balance rules; on failure the item is
>   `FAILED` for operations — never force-posted.
> Save these to memory `project_gl_posting_decisions` once implementation starts.

## Context
Card approvals reserve funds (cont. 35), but nothing ever turns a reservation into a debit, so **card spend never
reaches the customer's balance or the ledger** — it breaks "every money movement posts a balanced GL journal".
Exploration (3 agents, whole codebase) found:

- **card-service:** `SettlementService.addToCurrentBatch` has **no callers** (batches never fill); `closeBatch` jumps
  OPEN→SETTLED in one transaction, so the nightly export (reads `CLOSED`) never finds a batch; no backend call at
  settlement; `expireUnmatchedAuthorizations` says it releases holds but doesn't; interchange is computed only on an
  ADMIN request and in the **acquirer** direction (`net = gross − interchange − fees`); `recordAdvice` (0120/0220)
  always answers 00 and records a bare row. 0200 (ATM cash) places a hold and **nothing ever completes it** — it
  lapses after 7 days, so the customer keeps the cash and the money. Prepaid wallets have **no funding path and no GL**.
- **backend:** holds are `ACTIVE/RELEASED/EXPIRED` (V36 CHECK); no capture; `findByStatusAndExpiryDateBefore` exists
  but nothing calls it (only dormancy expires holds). `debitForSubledger` (MANDATORY) counts the hold being captured
  (`spendable = balance − active holds − floor`), so the hold must leave ACTIVE first. No card GL account, no
  `CARD` entity type, no card `TransactionType`. Next migrations: backend **V63**, card-service **V12**.
- **Decisions on record:** none for card clearing/interchange/prepaid (memory `project_gl_posting_decisions` covers
  loans/FX only: reject unmapped activity, no suspense fallback). CLAUDE.md "Settlement Modes" wrongly says
  settlement "posts final GL entries" — corrected here.

## Accounting treatment (checked before recommending)
- **Authorization (hold): no journal.** A reservation moves nothing; the deposit liability is unchanged until the
  bank becomes obligated to pay the scheme (IFRS 9 §3.3.1 — a liability is derecognised only when extinguished).
- **Clearing (presentment): DR customer deposit (2001, or overdraft 1102 via `balanceChangeLines`) / CR new GL 2300
  "Card Scheme Settlement Payable".** At presentment the bank owes the scheme on the cardholder's behalf: part of the
  deposit liability is extinguished (IFRS 9 §3.3.1) and a payable to the scheme is recognised (a contractual
  obligation to deliver cash — IAS 32 §11 financial liability).
- **Out of this PR (D3a):** interchange income + scheme fees (IFRS 15 in scope per PwC banking guidance; gross vs net
  presentation is an IFRS 15 B34–B38 principal/agent judgement — needs its own decision), and the cash leg when the
  scheme settles (DR 2300 / CR settlement bank; one net position per scheme fits IAS 32 §42 only with an enforceable
  right of set-off under the scheme rules). Prepaid: CBN treats prepaid card balances as deposit liabilities of the
  issuer (Guidelines on Stored Value/Prepaid Card Issuance, 2010/2012) — needs a funding path first.

## Changes — backend
- **V63:** GL 2300 Card Scheme Settlement Payable (LIABILITY, `manual_entries_allowed=false`) + activity
  `LIABILITY_CARD_SETTLEMENT`; hold status CHECK adds `CAPTURED`, `REFUNDED`; `account_holds` + `captured_amount`,
  `captured_at`. (Copy the V57–V60 seed pattern.)
- `FinancialActivity.LIABILITY_CARD_SETTLEMENT`; `JournalEntry.EntityType.CARD`; `TransactionType.CARD_PURCHASE`,
  `CARD_CASH_WITHDRAWAL`, `CARD_REFUND` (no CHECK on either column).
- **`POST /api/v1/accounts/{id}/card-holds/{reference}/capture`** `{amount, currencyCode}` (CARD_SERVICE/ADMIN):
  lock → hold ACTIVE and not past expiry → amount ≤ hold, same currency → hold → `CAPTURED` +
  `saveAndFlush` (so it stops counting) → debit (`debitForSubledger`-style, skipping the status/lock-in checks the
  authorization already passed) → journal DR deposit / CR 2300, ref = hold reference, entity CARD. Idempotent: same
  reference + amount on a `CAPTURED` hold returns the first result; other amount → `REFERENCE_CONFLICT`; `RELEASED`
  → `HOLD_RELEASED`. Partial capture frees the rest (hold amount stays as authorized; `captured_amount` records it).
  **Late presentment (D4a):** hold `EXPIRED` (or ACTIVE but past `expiry_date`) → the funds are no longer reserved,
  so the debit runs the full `checkDebitAllowed` rules (status, lock-in, spendable); refused → `approved=false` with
  the reason (`INSUFFICIENT_FUNDS`, `ACCOUNT_NOT_ACTIVE`, …), nothing posted. Never overdraws past the floor.
- **`POST …/card-debits`** `{reference, amount, currencyCode, cashWithdrawal}` — single-message (0200): same checks as
  `placeCardHold`, then hold row created directly as `CAPTURED` (reuses the unique `(account_id, external_reference)`
  index for idempotency) + debit + journal, one transaction.
- **`…/release` on a `CAPTURED` hold** = refund: credit back DR 2300 / CR deposit, status `REFUNDED` (0400 for an
  0200, e.g. ATM didn't dispense). Already `REFUNDED` → idempotent.
- `SecurityConfig` matchers + `RateLimitFilter.CARD_FUNDS_PATH` extended to `capture` and `card-debits`.
- **CoB `holdExpiryJob`** (after DORMANCY): `findByStatusAndExpiryDateBefore(ACTIVE, businessDate)` → `EXPIRED`,
  audited; `CobJobsIT` job lists + job docs updated.

## Changes — card-service (D1a, D2a)
- **V12:** `settlement_items` + `hold_reference`, `captured_amount`, `failure_reason`, `transaction_type`.
- Approval of a DEBIT/PREPAID purchase (0100) → `addToCurrentBatch` (its documented, never-wired role). 0200 DEBIT →
  backend `card-debits` instead of a hold (RC 51/57/91/94 mapping unchanged); item added already captured.
- **Scheduled batch close** (before the export cron): each PENDING DEBIT item → backend capture at the authorized
  amount → `SETTLED`/`FAILED` + reason; prepaid items → `SETTLED` with no GL (15c); reversed items → `REVERSED`.
  A failed capture never blocks the rest of the batch (one item per backend call; backend unreachable → item stays
  PENDING for the next close, not FAILED).
  Batch ends `CLOSED` (fixes the export job, which reads `CLOSED`). `expireUnmatchedAuthorizations` keeps marking
  stale PENDING items, with its false "releases the hold" Javadoc fixed.
- `reverse()`: marks the item `REVERSED`; for a captured original the backend release now refunds.
- Fix found on the way: `buildExportRecords` reads `al.rrn` as the auth code and blanks terminal id.

## Docs
CLAUDE.md (Settlement Modes correction, card approvals section, CoB job list, GL module "not posting yet"),
api-reference + static copy, Postman (+ static), OpenAPI snapshot, card-api-reference, deferred-backlog (15 → 15a
done; 15b interchange/settlement cash; 15c prepaid), cba-log cont. 45, plan copy `docs/plans/2026-10-21-backlog-15a-card-clearing-gl.md`
(+ mark the backlog-20 plan merged, #189 `c69b860`).

## Verification
- backend: `CardClearingIT` — hold → capture → balance down, GL DR 2001 / CR 2300 balanced per currency, trial balance
  balanced; partial capture; idempotent capture; capture of a released hold refused; 0200 `card-debits` + refund;
  concurrent capture; `ACTIVITY_NOT_MAPPED` when 2300 unmapped; expiry job flips status; CARD_SERVICE allowed,
  TELLER/CUSTOMER refused. `-Pfull-integration` all green (was 924).
- card-service: unit (batch fill on approval, close → capture/FAILED/REVERSED, 0200 path, reversal of captured) +
  WireMock IT for capture/card-debits calls. `-Pfull-integration` all green (was 174).
- Gate grep: new endpoints → api-reference + Postman entries required.
