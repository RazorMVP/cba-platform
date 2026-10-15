> **Status:** merged — PR #154 (same PR, security-review extension), merge commit `0ba2ff5`. Approved 2026-10-15.  
> Recovered verbatim from the session transcript (the approved `ExitPlanMode` plan); the body below is unedited.

# PR #154 extension — card approvals reserve funds (atomic check-and-hold), released on reversal

## Context
The commit security review on #154 found a double-spend: a card approval reserves nothing.
- card-service checks funds (`GET /api/v1/accounts/{id}/balance`, read-only, no lock) and approves, but places no hold and debits nothing.
- Two $400 approvals both pass against a $500 balance, and so would any number of them.
- **Debit:** before #154 every debit authorization declined RC 91 (fail-safe), so #154 makes this reachable for the first time.
- **Prepaid:** already exposed before #154. No code anywhere changes a wallet balance, and the wallet read takes no lock.

The user chose option 1: atomic authorize-and-hold in #154.

**What exists** (exploration):
- **Backend:**
  - `AccountService.placeHold` locks with `findByIdWithLock` (PESSIMISTIC_WRITE) and checks `balance − active holds − floor`;
  - `spendable()` already subtracts every ACTIVE hold, so a card hold reduces all later card checks, withdrawals and payments;
  - holds post no GL (correct: a hold is a restriction, not a movement).
- **Missing on the backend:**
  - an idempotency key: `reference_number` is overwritten with a timestamp and has no unique index;
  - a CARD_SERVICE endpoint;
  - any hold-expiry sweep: `findByStatusAndExpiryDateBefore` is never called, and only dormancy expires holds.
- **card-service:**
  - `authorize` approves after the funds step with nothing reserved;
  - `reverse` finds the original by card + STAN and calls itself "the seam" for a financial reversal;
  - `authorization_log` has no column for a reservation;
  - `PrepaidWallet` has `@Version` but its repository has no locking read.

**Scope:** reserve on approval, release on reversal, expire on the authorization lifetime. Debiting at clearing/settlement and the GL for card spend remain **backlog 15**: settlement batches are never even filled (`addToCurrentBatch` has no callers).

## Decisions (recommended defaults)
- **H1. The hold is the reservation, made atomically in the backend:**
  - one `POST` locks the account, applies the withdrawal rules (active, lock-in, currency, spendable) and inserts the hold in one transaction;
  - a decline is a **200 with `approved=false` + reason**, not an error, so card-service maps it to an RC.
- **H2. Idempotency key = deterministic reference** `CARD-{cardId}-{stan}-{rrn}`.
  - A retried 0100 returns the same hold, never a second one.
  - The backend enforces it with a **full** unique index on `(account_id, external_reference)`, so a retry arriving after release can't re-hold.
- **H3. Expiry:**
  - card holds get `expiry_date = today + card.settlement.auth-expiry-days` (7, the scheme authorization lifetime the code already assumes);
  - `spendable()` ignores ACTIVE holds whose `expiry_date` has passed (`sumActiveHoldsByAccount` adds `expiry_date IS NULL OR expiry_date >= today`), so a stale hold stops blocking funds without a new job;
  - a status sweep (ACTIVE → EXPIRED) is a follow-up (backlog 15).
- **H4. Prepaid:** the wallet *is* the stored value, so approval **decrements it under a pessimistic lock** and a reversal restores it. The amount is recorded on the authorization log row. Prepaid GL stays in backlog 15.
- **H5. Balance inquiry** (310000) still uses the read-only `GET …/balance`, with no hold. **0200** (ATM cash / auth-and-capture) also holds; nothing completes it until clearing exists (backlog 15), so it lapses after 7 days.

## 1. Backend
- **`V62__card_holds.sql`:**
  - `account_holds` gets `external_reference VARCHAR(64)`;
  - unique index `ux_account_holds_external_ref ON account_holds(account_id, external_reference) WHERE external_reference IS NOT NULL`.
- **`AccountHoldRepository`:**
  - `findByAccountIdAndExternalReference`;
  - `sumActiveHoldsByAccount(accountId, today)` ignores expired holds (update every caller: `spendable`, `toResponse`, `PaymentService` call sites).
- **`AccountService`:**
  - **`placeCardHold(accountId, CardHoldRequest{reference, amount (major units), currencyCode})`:**
    1. lock the account (`findByIdWithLock`);
    2. if the reference already exists, return that hold's result (idempotent);
    3. otherwise apply the same rules as `cardFunds`/`checkDebitAllowed` (active, lock-in, currency ≠ account → `CURRENCY_MISMATCH`, `spendable < amount` → `INSUFFICIENT_FUNDS`);
    4. on success insert a hold (reason `Card authorization {reference}`, expiry today + 7, `created_by` = the caller);
    5. audit `CARD_HOLD_PLACED`.
    - Returns `CardHoldResult{approved, holdId, reference, reason, availableBalance after}`.
  - **`releaseCardHold(accountId, reference)`:** lock the account. An ACTIVE hold → RELEASED (audit `CARD_HOLD_RELEASED`). Already released, expired or unknown → no-op, `{released:false, status}`. Idempotent: a duplicate reversal never errors.
- **`AccountController`:**
  - `POST /api/v1/accounts/{id}/card-holds` and `POST /api/v1/accounts/{id}/card-holds/{reference}/release`;
  - `@PreAuthorize("hasRole('CARD_SERVICE') or hasRole('ADMIN')")`;
  - DTOs `CardHoldRequest` (`@NotBlank @Size(max=64) reference`, `@DecimalMin("0.01") amount`, `@NotBlank currencyCode`) and `CardHoldResult`.
- **`SecurityConfig`:** `POST /api/v1/accounts/*/card-holds` and `POST /api/v1/accounts/*/card-holds/*/release` → CARD_SERVICE, ADMIN, placed before the `POST /api/v1/**` catch-all.
- **`RateLimitFilter`:** extend the exemption regex to these paths (same reason as `/balance`).

## 2. card-service
- **`V11__authorization_reservation.sql`:** `authorization_log` gets `hold_reference VARCHAR(64)` and `wallet_debit NUMERIC(19,4)`.
- **`CardAuthorizationService.authorize`, debit and not a balance inquiry:**
  - Instead of `GET /balance`, `POST …/card-holds` with `CARD-{cardId}-{stan}-{rrn}`, the amount in major units, and the alphabetic currency (`Currency.getCurrencyCode()`).
  - Map the result: `approved` → continue; `INSUFFICIENT_FUNDS` → 51; `ACCOUNT_NOT_ACTIVE` / `ACCOUNT_IN_LOCKIN_PERIOD` / `CURRENCY_MISMATCH` → 57; unreachable → 91.
  - On approval, set `hold_reference` on the log row.
  - If anything fails after the hold was placed (the outer catch), release it best-effort so it doesn't linger.
- **Prepaid:**
  - `PrepaidWalletRepository.findByCardIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`);
  - check → decrement balance → save;
  - record `wallet_debit` on the log row. All in `authorize`'s transaction, so a rollback restores it.
- **`reverse`:** after the existing idempotency guard and original lookup, only when the original was approved (`"00"`):
  - release its `hold_reference` (backend, best-effort; logged);
  - restore its `wallet_debit` to the wallet under the lock.
  - The existing `0400` guard keeps a duplicate reversal from releasing or restoring twice.
- **`logAndReturn`:** accept the reservation fields; the approve path passes them.

## 3. Tests
- **Backend `AccountServiceTest`:**
  - `placeCardHold`: approve; same reference twice → one hold, same id; insufficient; not active; lock-in; currency mismatch.
  - `releaseCardHold`: release; twice → no-op; unknown → no-op.
  - `spendable` ignores an expired ACTIVE hold.
- **Backend `CardHoldsIT`** (real PG):
  - **double-spend:** two threads each try 400.00 against a 500.00 account → exactly one approved; holds total 400;
  - a release frees the funds;
  - HTTP envelope;
  - CARD_SERVICE allowed, CUSTOMER and TELLER denied;
  - retry after release → same reference, not re-held.
- **card-service `CardAuthorizationServiceTest`:**
  - debit approval POSTs the hold with the right reference, major amount and alphabetic currency;
  - decline mapping 51/57/91;
  - balance inquiry uses GET with no hold;
  - reversal releases the hold once (a duplicate doesn't);
  - prepaid approval decrements and reversal restores.
- **card-service IT `CardReservationIntegrationTest`:**
  - a real PREPAID card via `CardService.issueCard` with a known PAN and a 50.00 wallet;
  - authorize 30.00 → 00; authorize 30.00 → **51** (reserved); reverse the first → wallet back to 50.00; authorize 30.00 → 00.
  - Debit over the WireMock backend: POST card-hold carries a Bearer token and the reference.
- Regenerate both OpenAPI snapshots if paths change (backend: 2 new paths).

## 4. Docs
- `docs/api-reference.html` + Postman (+ docs-site copies): the two card-hold endpoints.
- `docs/card-api-reference.html`: approvals reserve funds; reversal releases; RC mapping.
- `docs/deferred-backlog.md` item 15 narrowed to: completion/debit at clearing, GL for card and prepaid spend, the hold status sweep, 0200 completion.
- `CLAUDE.md`; `cba-log.md` cont. 35 updated (same PR).

## 5. Verification
1. **Baseline:** a backend IT on the current branch shows two concurrent 400.00 card approvals against 500.00 both pass (no reservation).
2. Backend and card-service `-Pfull-integration` green; fep-service unaffected.
3. Gate grep → docs → push to #154 → CI green (incl. SpotBugs) → merge on the user's word.
