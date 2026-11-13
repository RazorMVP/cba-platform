# Backlog set 3: 15d (the buildable rest) + 15c (leftovers)

> Status: approved 2026-11-13 (user: "set 3 = 15d+15c", "start 15d and 15c", plan approved; every decision a). PR A (15d partial refunds, D1a) in progress — `feat/card-partial-refunds`.

## Context

| # | Problem today | Outcome |
|---|---|---|
| 15d-refund | A captured debit or a cleared prepaid purchase can be refunded only **once**. A second partial reversal of the same purchase (different STAN) gets `ALREADY_REFUNDED` → RC 13, and the customer's money stays with the merchant side until someone fixes it by hand. card-service keeps one refund slot per settlement item | Any number of partial refunds up to the captured / cleared amount. Each refund has its own journal, its own pro-rata share of interchange and fees, and its own row in the scheme report's expected net, on its own date |
| 15d-fees | Cross-border and currency-conversion scheme fees are seeded (Visa, Mastercard, Afrigo, UnionPay) but filtered out. The acquirer's country (DE19) reaches card-service and is then dropped. So the expected net under-states what the scheme bills, and the difference lands on GL 1004 | DE19 is stored. The quote adds `CROSS_BORDER` when the acquirer's country isn't the card's, and `INTERNATIONAL_SERVICE` when the transaction currency isn't the card's. They post with the other scheme fees |
| 15d-recon | Two failure states have no list anywhere: (1) declined RC 91 authorizations whose compensating hold release never reached the backend (`authorization_log.hold_reference` set); (2) settlement items stuck in `CLEARING` after a batch close died | A Reconciliation tab on the Settlement screen lists both, plus authorizations whose country was unknown. Ops can retry a hold release from the list |
| 15c-limits | A wallet can be loaded without limit | Per-currency maximum balance and daily load total, with a per-product override |
| 15c-fees | Load fees are listed as "not built" | **Not built, on purpose** (D5): CBN forbids charging for prepaid loading/unloading |

**Packaging:** four PRs, merged in this order (migrations are numbered in merge order). Each is merged when you say "merge N" and CI is green.

| PR | Items | Services | Migrations |
|---|---|---|---|
| **A** | 15d partial refunds | backend, card-service | backend V70, card-service V19 |
| **B** | 15d cross-border / currency fees | card-service, web | card-service V20 |
| **C** | 15d reconciliation views | card-service, web | card-service V21 |
| **D** | 15c wallet limits + load-fee closure | card-service, web | card-service V22 |

## Rules checked before recommending
- **CBN Revised Guidelines on Stored Value/Prepaid Card Issuance and Operations** (circular BPS/DIR/GEN/CIR/01/011, 10 Oct 2012, effective 2 Oct 2012):
  - §4.2 / §4.3: a *stored value* card loads at most ₦50,000 a day and holds at most ₦250,000.
  - §5.1 / §5.3: for *prepaid* cards the issuing bank sets the load limits and balances.
  - §5.2: "No prepaid card shall be issued beyond the limits of a stored value card … Full KYC would be required."
  - §3.7: the holder can always get the balance refunded.
- **CBN Guide to Charges by Banks, Other Financial and Non-Bank Financial Institutions** (effective 1 Jan 2020, circular FPR/DIR/GEN/CIR/07/042):
  - §10.6.4: "Prepaid Card Loading/Unloading — **No charge**".
  - §10.7.3: international ATM withdrawals at "exact cost by international acquirer (cost recovery)".
  - Preface: any charge not in the Guide needs CBN's prior written approval.
  - A revised Guide was published as an exposure draft in April 2026 and reported effective 1 May 2026. I could not find its prepaid row, so the 2020 text is the reference until the final 2026 text is read.
- Scheme-fee accounting stays on the 15b decisions in memory `project_gl_posting_decisions` (gross; IFRS 15 B35/B37; IAS 1 §32; 2300 net per scheme, IAS 32 §42). Refund pro rata follows 15b (IFRS 15 §38).

## Decisions for you
Reply "go" to take every recommendation, or name one to change it (e.g. "D4 b").

**D1 (15d): more than one partial refund of the same purchase.**
- **a (recommended):** allow refunds until the captured (debit) or cleared (prepaid) amount is used up.
  - Each refund is its own row, keyed by card-service's `reversalReference`. A retry of the same reference returns the original (repeat); any other reference is a new refund.
  - Charges are reversed **cumulatively**: this refund's share = pro rata of the total refunded so far, minus what earlier refunds already reversed. The shares add up to exactly the posted figures once the purchase is fully refunded, with no rounding drift.
  - An amount above what's left → `AMOUNT_EXCEEDS_CAPTURED` (prepaid: `AMOUNT_EXCEEDS_CLEARED`), RC 13 as today.
  - The hold stays `CAPTURED` until fully refunded, then `REFUNDED`.
  - No new accounting: each refund posts the 15b refund journal for its own amount.
- b: keep "refund once". A second partial reversal stays RC 13 for ops to settle by hand.

**D2 (15d): cross-border and currency-conversion scheme fees.**
- **a (recommended):**
  - `CROSS_BORDER` applies when DE19 (acquirer country) differs from the card's BIN-range country. `INTERNATIONAL_SERVICE` applies when the transaction currency (DE49) differs from the card's currency, as `SchemeFeeType` already documents.
  - They are scheme charges to the issuer, so they join the item's scheme fee and post DR **5006 Card Scheme Fees** / CR 2300, exactly like assessment and network fees (15b: IFRS 15 B37, the scheme is a supplier; IAS 1 §32 no netting). No new GL account: same kind of expense (IAS 1 §29). card-service keeps the split on the item for reporting.
  - **Not passed on to the cardholder.** A foreign-transaction fee on purchases isn't in the CBN Guide to Charges, so it would need CBN's prior approval (Guide, Preface).
  - **Country unknown** (no DE19, an unknown code, or a BIN range with no country): no cross-border fee, and the item is flagged `country_unknown` so it shows on the Reconciliation tab. A scheme that did charge it shows up as a scheme-report difference.
- b: charge the cross-border fee when the country is unknown (assume foreign). Over-states the expected net for domestic acquirers that don't send DE19.
- c: leave them out, as today.

**D3 (15d): what the Reconciliation tab can do.**
- **a (recommended):** list the three cases. For an unresolved hold, offer **Retry release**: the backend release is idempotent, and a reference it never saw is stored as a tombstone, so a late hold can't commit afterwards. A resolved row records who, when and the outcome. A stuck `CLEARING` item links to its batch, where **Close** already retries the capture. ADMIN only.
- b: read-only lists.

**D4 (15c): wallet limits.**
- **a (recommended):**
  - Configured per wallet currency in card-service: `card.prepaid.limits.<CCY>.max-balance` and `.daily-load`.
  - **Default NGN: ₦250,000 maximum balance, ₦50,000 loaded per day.** §5.2 holds prepaid cards to the stored-value limits until the customer has full KYC, and the backend has no KYC tier to prove that. Other currencies have no default limit (§5.1: the issuer decides).
  - A card product can override both (new nullable columns on `card_products`) for full-KYC products.
  - Enforced on **loads only**: balance + loads still pending + this load ≤ maximum; today's loads (pending or completed, calendar day in `Africa/Lagos`) + this load ≤ daily limit. Refused with `400 WALLET_BALANCE_LIMIT` / `DAILY_LOAD_LIMIT`.
  - Never blocks money coming back (a reversal, a refund, an expired item): it is already the customer's money (§3.7).
  - Two concurrent loads are serialised by a per-card advisory lock in the claim transaction.
- b: the same switches, but no default; nothing is limited until configured.

**D5 (15c): load fees.**
- **a (recommended):** don't build them. CBN Guide to Charges §10.6.4 says "No charge" for prepaid loading and unloading. The backlog records this with the citation, to be re-checked against the final 2026 Guide.
- b: a configurable load fee for foreign-currency wallets only, default 0, switched on after CBN approval.

## PR A — partial refunds (D1)

**backend (V70):**
- `card_hold_refunds(id, hold_id FK, refund_reference UNIQUE, amount, interchange_reversed, scheme_fee_reversed, journal_transaction_id, created_by, created_at)`. Backfill one row per existing `REFUNDED` hold (reference `LEGACY-<hold id>` where `refund_reference` is null).
- `account_holds.refunded_amount`, `interchange_reversed`, `scheme_fee_reversed` become running totals.
- `AccountService.releaseCardHold` / `refundCardDebit` (`account/AccountService.java:599-696`):
  - `CAPTURED` or `REFUNDED`: a known `reversalReference` → repeat; otherwise refund up to `captured − refunded`.
  - Cumulative pro rata through the existing `prorate` helper (lines 546-549): share = `prorate(posted, refundedBefore + amount) − reversedBefore`.
  - `CardHoldResult` gains `refundedAmount` (running total) and `refund {reference, amount, interchangeReversed, schemeFeeReversed}` for this call.
- `CardPrepaidService.refund` (`cardprepaid/CardPrepaidService.java:157-204`): drop the "once" check; the clearing's `refunded_amount` becomes a running total; REFUND rows already exist per refund, so their sums give "reversed so far". Align its `proRata` with the account one (`.min(figure)`, digits fallback).
- No GL change: each refund calls `postCardSettlement(..., reversal=true)` / `postPrepaidCardClearing(..., reversal=true)` for its own share.

**card-service (V19):**
- `settlement_refunds(id, item_id FK, reversal_reference UNIQUE, amount_minor, interchange_reversed, scheme_fee_reversed, refund_gl_net, refund_settlement_date, refund_report_id, created_at)`. Copy the existing single-slot refunds from `settlement_items`, then drop those columns; `settlement_items.refunded_amount` stays as the running total.
- `SettlementService.recordRefund` (lines 143-160) writes a row keyed by `reversalReference` (idempotent) instead of the once-only slot.
- `CardAuthorizationService.reverse` (lines 483-508): record the refund from the new `refund` block of the backend answer (debit) or the prepaid refund result, not from `status == REFUNDED`.
- `SchemeSettlementService.expectedFor` / `prepare` and `SettlementItemRepository.findUnreportedRefunds` (lines 47-49) read `settlement_refunds` by `refund_settlement_date`; recording a report stamps the refund rows.

**Tests:**
- backend `CardClearingIT`: two partials summing to the capture reverse fees exactly; a third over the remainder refused; same reference twice is a repeat; a backfilled hold refunds again correctly. `CardPrepaidIT`: two partial refunds, then `AMOUNT_EXCEEDS_CLEARED`.
- card-service `CardClearingIntegrationTest`: two partial reversals with different STANs → both RC 00, two refund rows, wallet credited twice (prepaid). `SchemeSettlementService` test: refunds on two dates land in their own day's expected net. V19 backfill check.

## PR B — cross-border / currency fees (D2)

- **card-service V20:** `authorization_log.acquiring_country VARCHAR(3)` (DE19 as sent); `settlement_items.cross_border_fee`, `currency_fee` (minor units), `country_unknown BOOLEAN DEFAULT false`.
- `CardAuthorizationService.logAndReturn` (lines 809-832) stores `req.acquiringCountry()`.
- Pull the country comparison out of `blockingControl` (lines 756-765) into one helper `CardCountry.crossBorder(card, de19)` returning `DOMESTIC | CROSS_BORDER | UNKNOWN`, used by both the control (UNKNOWN still fails closed there) and the quote.
- `InterchangeQualificationEngine.quote` (line 64): add the cross-border state and the transaction currency; apply `CROSS_BORDER` rows only when cross-border, `INTERNATIONAL_SERVICE` rows only when the currencies differ (`APPLIED_FEES`, line 51, becomes per-transaction). `IssuerCharges` gains the two parts; `schemeFee` stays their total, so the backend contract doesn't change.
- Callers: `SettlementService.quoteCharges` (lines 470-483) and the 0200 path `CardAuthorizationService.quoteCharges` (lines 680-689).
- Auths logged before V20 have no country → treated as UNKNOWN (flagged, no fee).
- Web: the Interchange screen's Scheme Fees tab labels the two fee types and says when they apply.
- Tests: engine unit tests (domestic, cross-border, currency, both, unknown); the 0200 debit quote carries the fee; batch close stores the split and the flag.

## PR C — reconciliation (D3)

- **card-service V21:** `authorization_log.hold_resolved_at`, `hold_resolved_by`, `hold_resolution`.
- `GET /api/v1/cards/settlement/reconciliation` (ADMIN) → `{unresolvedHolds[], stuckClearing[], countryUnknown[]}` for an optional date range:
  - unresolved holds: `response_code='91' AND hold_reference IS NOT NULL AND hold_resolved_at IS NULL`;
  - stuck clearing: items `CLEARING` whose batch date is before today;
  - country unknown: items with `country_unknown`.
- `POST /api/v1/cards/settlement/reconciliation/holds/{authLogId}/release` (ADMIN): calls the existing release path (`CardAuthorizationService` lines 717-745) with the original amount and currency; on DONE or HOLD_NOT_FOUND it stamps the resolution; unreachable → 503, nothing stamped.
- Web: Settlement screen gets a fourth tab, **Reconciliation** (same patterns as the Scheme Reports tab: date filter, `data-table`, status badges, `.error-banner`), with Retry release per hold and a link to the batch for stuck items. `CardsService.reconciliation()` / `retryHoldRelease()` + specs.

## PR D — wallet limits + load-fee closure (D4, D5)

- **card-service V22:** `card_products.max_wallet_balance`, `daily_wallet_load` (NUMERIC(19,4), nullable, major units).
- `PrepaidLimitProperties` (`@ConfigurationProperties("card.prepaid")`, map currency → `{maxBalance, dailyLoad}`), with NGN 250000 / 50000 in `application.yml` and `card.prepaid.limit-zone: Africa/Lagos`.
- `WalletService.load` (lines 79-95): in the claim transaction take `pg_advisory_xact_lock(hashtext('wallet:' + cardId))`, pick the product override else the currency default, check, and refuse before saving the movement. `WalletMovementRepository` gets sums of pending loads and of today's loads.
- `CardProductController` / `CardProductsComponent`: the two limit fields.
- Web card detail: error text for `WALLET_BALANCE_LIMIT` and `DAILY_LOAD_LIMIT` (`walletErrorText`, `card-detail.ts:165`), and the remaining allowance in the wallet card (`GET /{id}/wallet` returns `limits {maxBalance, dailyLoad, loadedToday}`).
- `docs/deferred-backlog.md` 15c: load fees closed as won't-do with the CBN §10.6.4 citation.
- Tests: `WalletServiceTest` (balance cap, daily cap, pending loads counted, product override, other currencies unlimited); an integration test with two concurrent loads that together exceed the cap (one refused); a reversal that takes the balance over the cap still succeeds.

## Every PR
- Branch off main; PR; merge only on your "merge N" with the required gates green (SonarCloud is non-blocking, backlog 9).
- Docs: `CLAUDE.md` (module notes, Last git commit rows), `cba-log.md` (cont. 63–66), `docs/api-reference.html` + `docs/cba-postman-collection-v2.json` (+ `docs-site/static` copies), `docs/card-api-reference.html`, regenerated OpenAPI snapshots, `docs/deferred-backlog.md`. Run the session docs-gate grep and paste its output.
- Close-out: one docs PR marks this plan merged with the PR numbers and SHAs.

## Verification
- backend: `cd backend && ./mvnw clean verify -Pfull-integration` (now 1121) — PR A.
- card-service: `cd card-service && DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw clean verify -Pfull-integration` (now 320) — every PR; snapshot regenerated with `-Dupdate.api.snapshot=true`, then run again without it.
- web: `cd web && CI=true npx ng test --no-watch` (now 1186) + `npm run check:change-detection` + `npm run build` — PRs B, C, D.
- After each merge: `git log --oneline -1`; migration list has no gaps (backend V70; card-service V19 → V22).

## Not in this set
- Clearing-file intake (amount differences, incremental presentments): needs the schemes' clearing formats (blocked, like items 7 and 22).
- Pre-V13 partial refunds that aren't in card-service's settlement items: still reconcile by hand.
- A KYC tier on customers (would let D4 lift the NGN cap per customer instead of per product).
