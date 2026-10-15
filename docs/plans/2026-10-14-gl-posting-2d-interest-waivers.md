> **Status:** merged — PR #149, merge commit `298975a`. Approved 2026-10-14.  
> Recovered verbatim from the session transcript (the approved `ExitPlanMode` plan); the body below is unedited.

# GL posting PR 2d — interest waivers (IFRS 9 derecognition + §5.4.3 modification) + LOANS opening balances + loan-extension guards

## Context
Last piece of PR 2 (loans GL).
- `POST /loans/{id}/waive-interest` (`LoanService.waiveInterest:502-528`) posts **nothing** and corrupts the sub-ledger. It sets `interestPaid = interestDue` on every unpaid instalment, future ones included, and never refreshes `totalPaid` or the status.
  - Effects: 1101 goes negative, so ECL EAD and write-off amounts are wrong.
  - Accrual keeps accruing the waived interest.
  - `closeOut` books the waived future interest as **income** at payoff.
  - Suspense is never cleared, and DPD never drops.
- `OpeningBalanceService` has only the `DEPOSITS` scope.
- Reschedule / re-age / re-amortise only record a request: no guards, and approve changes nothing.

**Decisions (user "1a 2a 3a 4a", 2026-10-14; standards checked first):**
- **1a** Waived *accrued, unpaid* interest = derecognition (IFRS 9 §3.2.3(a); IFRIC agenda decision *Lessor Forgiveness of Lease Payments*, Oct 2022, by analogy).
  - The part covered by suspense: DR 2200 / CR 1101, no P&L (CBN PG 2010 §15.8, §15.2).
  - The rest: DR loss / CR 1101.
  - Then remeasure ECL (IASB staff AP27C Apr 2024 ¶27, ¶33).
- **2a** Loss line = new GL **5005 Loan Modification and Waiver Losses** (ITG AP8 2015 ¶25–26; disclosure under IFRS 7 §35J).
- **3a** Waived *future* interest = §5.4.3 modification.
  - Loss L = PV of the waived cash flows at the loan's **original EIR**: DR 5005 / CR new contra-asset **1106 Loan Modification Discount**.
  - 1106 unwinds into interest income over the remaining term on an EIR basis (IFRS 9 IE68–70).
  - Non-performing loans post no modification loss. The GL holds stage-3 interest on CBN cash-basis suspense (PR 2c decision 1a), so that interest would never have reached income. Lifetime P&L is identical; only timing differs. Documented as an IFRS vs CBN divergence.
- **4a** Loan-extension requests get guards only; approval records the decision. Schedule changes come in PR 2e.

Branch `feat/gl-posting-loans-2d` off `main` (`2540fea`), one PR. Merge only on the user's word with CI green.

## 1. Migration `V60__loan_interest_waivers.sql`
Pattern: V57–V59 (`INSERT … SELECT v.* … WHERE EXISTS parent … ON CONFLICT DO NOTHING`).
- GL 1106 *Loan Modification Discount*: ASSET contra, id `30000000-0000-0000-0000-00000000000c`, parent `…0001`.
- GL 5005 *Loan Modification and Waiver Losses*: EXPENSE, id `…000000000045`, parent `…0040`.
- Both `manual_entries_allowed FALSE`.
- Activity mappings: `ASSET_LOAN_MODIFICATION_DISCOUNT` (32 chars) and `EXPENSE_LOAN_MODIFICATION_LOSS` (30). The column is VARCHAR(50).
- `loans`: `effective_daily_rate DOUBLE PRECISION` (null = not yet computed), `modification_discount_remaining NUMERIC(19,4) NOT NULL DEFAULT 0`.
- `loan_repayment_schedule`, each `NUMERIC(19,4) NOT NULL DEFAULT 0`:
  - `interest_waived`: accrued interest derecognised; stays inside `interest_due`.
  - `interest_cancelled`: future interest removed from `interest_due` (informational).
  - `modification_unwind`, `modification_unwound`.
- **No backfill for loans hit by the old waiver.** Every formula uses `paid + waived`, so the inflated `interest_paid` already behaves like a waiver. The real damage is income PR 2b accrued on "waived" future interest, which a migration can't correct safely.
  - Put a detection query in a comment and in cba-log: audit `WAIVE_INTEREST` rows without a `scope` key, plus the signature `total_paid < principal_paid + interest_paid + fees_paid`.
  - The LOANS opening preview will show the 1101 break. Fix it by manual journal.
- `FinancialActivity` enum: the two new values.

## 2. Backend

### Schedule helpers (`loan/LoanRepaymentSchedule`)
Centralise the formulas so no call site forgets waivers:
- `unpaidPrincipal()`, `unpaidFees()`.
- `unpaidInterest()` = max(0, interestDue − interestPaid − interestWaived).
- `interestReceivable()` = accrued − paid − waived (signed).
- `isSettled()` = status ∈ {PAID, WAIVED}.
- `refreshTotals()`: `totalDue` = P + I + F − interestWaived (net payable), `totalPaid` = sum of the paid parts.
- `refreshStatus(date)`: WAIVED when nothing is unpaid and waived > 0, PAID when nothing is unpaid, else PARTIALLY_PAID if anything was paid.

**Call sites to switch to the helpers** (design verified these, file:line):
- `LoanService`:
  - `allocate` (298/302), `quote` (330-345), `forecloseLoan` (553-568: kept = max(accrued, paid + waived)), `scheduledOutstanding` (651-656), the undo-write-off fallback (483-486).
  - The `markPaidStatus` / local helpers are deleted.
- `LoanAccrualService`: `updateArrears` (76-80), `classify` (101-103), `closeOut` (prepaid = paid + waived − accrued).
- `LoanImpairmentService`: `interestReceivable` (138-142), `prudentialProvision` (197), `exposure` (also − `modificationDiscountRemaining`).
- `LoanRepaymentScheduleRepository.countPaidBetween`: status IN ('PAID','WAIVED').
- V9 ActiveLoans, `LiquidityService` and the dashboard read `total_due`, which is now net, so they need no change.

### Original EIR (`loan/EffectiveInterest`)
- Store `effectiveDailyRate` at disbursement: `dailyRate(amount − fee, disbursementDate, schedule)`.
  - Add a `feeAmortisation(..., rate)` overload so the rate is computed once.
  - Fee = 0 gives the contract rate; `EffectiveInterestTest` already covers this.
- `originalDailyRate(Loan)`: lazy for loans disbursed earlier, computed from the still-contractual `interestDue` **before** any schedule change. Never recomputed.
- New package-private `discount`, `modificationLoss(cashFlows, D, r)` and `modificationUnwind(loan, D, r, targetAtD)`:
  - Recursion on modified cash flows: G_k = G_{k−1}(1+r)^days − CF_k.
  - T_k = round4(ledgerCarrying_k − G_k), clamped to [0, T_{k−1}], with T_last = 0.
  - unwind_k = T_{k−1} − T_k, so Σ telescopes exactly to T_D = R + L.
  - Recomputed on every waiver, so several waivers stack.
- **Verified example:** 1,000 at 24%, 2 monthly instalments, waive ALL at day 0 → L = 29.3083, unwinds 20.0633 + 9.2450. Waive at day 10 → L = 23.1396, unwinds 13.8945 + 9.2450.

### Waiver (`LoanService.waiveInterest` rewrite)
**`WaiveInterestRequest`** = `reason`, `scope` (`ACCRUED | ALL`, default ALL = today's meaning), `waiveDate` (default today), `approvalReference` (≤100). Keep the 1-arg constructor.

**Validation:** open loan (`INVALID_LOAN_STATE`); `eventDate` (not future, not before disbursement); not before `interestAccruedThrough` (`WAIVE_DATE_BEFORE_LAST_ACCRUAL`, because `accrueThrough` can't move backwards); `NOTHING_TO_WAIVE`.

**Order:**
1. `bringCurrent(loan, D)`.
2. Get the rate if scope = ALL.
3. Per unsettled instalment (W = interestWaived before the waiver):
   - recv = accrued − paid − W; accruedWaived = max(recv, 0); W += accruedWaived.
   - futureWaived (ALL only) = max(interestDue − max(accrued, paid + W), 0).
   - interestDue −= futureWaived; interestCancelled += futureWaived.
   - `refreshTotals` / `refreshStatus`.
   - Accrual then can't exceed accrued, because `accruedBy(total)` ≤ total.
4. **IWV** journal: DR 2200 `min(accruedWaived, interestInSuspense)`, DR 5005 rest, CR 1101. Reduce `interestInSuspense`.
5. Performing and ALL: **MOD** journal DR 5005 L / CR 1106; `modificationDiscountRemaining += L`.
6. Recompute the unwind when L > 0 or R > 0.
7. `updateArrears` + `classify`.
8. `loanImpairmentService.remeasure(loan, D)`.
9. Audit `WAIVE_INTEREST` with scope, date, accruedWaived, futureWaived, fromSuspense, losses, approvalReference and reason.

### GL (`loan/LoanGlPosting`)
- New methods:
  - `postInterestWaiver(loan, fromSuspense, loss, date)` (IWV).
  - `postModificationLoss(loan, L, date)` (MOD).
  - `postModificationUnwind(loan, amount, toSuspense, date)` (MODU: DR 1106 / CR interest income, or CR 2200 when non-performing).
- MODU is a separate journal, so the existing `postAccrual` verifies stay valid.
- **1106 and 5005 are resolved only when the amount is > 0** (`gl.activityAccount` throws when unmapped).
- `WriteOffAmounts` + `modificationDiscount` (keep the 6-arg constructor): `gross()` subtracts it, and `postWriteOff` adds DR 1106 when > 0.
- Write-off snapshot / zeroing / undo restore include `modificationDiscountRemaining`. WOFFREV mirrors it automatically.

### Accrual (`loan/LoanAccrualService`)
- **In `accrueThrough`:** incremental unwind release per instalment.
  - start = max(periodStart, from).
  - add = (unwind − unwound) × days(start, date)/days(start, due), rounded down to 4dp; everything once due.
  - Post MODU; `modificationDiscountRemaining −= add`; if non-performing, `interestInSuspense += add`.
- **In `closeOut`:**
  - Mark unwound = unwind.
  - Release the remaining 1106 to income.
  - Release any leftover suspense (`postInterestSuspenseRelease`).
  - Zero both.

### Opening balances (`accounting/OpeningBalanceService`)
- `Scope { DEPOSITS, LOANS }`; `compute(scope)` switches SQL.
- `LOANS_SQL` is one statement, like DEPOSITS, with activity ids bound once in a `p` CTE. Over loans with status ACTIVE / IN_ARREARS (written-off loans keep schedule receivables in the sub-ledger), per GL × `loan_products.currency_code`:

| GL | Customer side |
|---|---|
| 1100 | `outstanding_balance` (`COALESCE(product loan_portfolio_account_id, default)`) |
| 1101 | Σ(`interest_accrued − interest_paid − interest_waived`) |
| 1103 | recognised, unpaid, unwaived charge `amount_outstanding` |
| 1104 | −`deferred_fee_remaining` |
| 2200 | −(`interest_in_suspense` + suspended charges) |
| 1105 | −`loss_allowance` |
| 1106 | −`modification_discount_remaining` |

- The ledger side and the FULL OUTER JOIN are the same as DEPOSITS. Reference `OPENING-LOANS`, lock `opening-balance-LOANS`.
- An unmapped activity throws `ACTIVITY_NOT_MAPPED` only when its net ≠ 0.
- Document the migration prerequisite: fill `interest_accrued` / `interest_accrued_through` before posting. Otherwise the first nightly run accrues legacy interest as today's income (ISA 510 / IAS 8).
- `GlAccountingController` scope text updated.

### Loan extensions (`loan/LoanExtensionService` + controllers)
- Create/approve need an open loan (`INVALID_LOAN_STATE`).
- Approve/reject need PENDING (`EXTENSION_REQUEST_NOT_PENDING`).
- Audit APPROVE / REJECT.
- `approveReaging(loanId, id)` / `approveReamortization(loanId, id)` use the path `loanId`; a mismatch returns 404.
- Unknown reschedule `command` → `400 UNKNOWN_COMMAND` (was a 500).

### Responses
- `LoanResponse` + `interestWaived` (Σ waived + cancelled) and `modificationDiscountRemaining`. Both are nulled in `forCustomer()` like the other internal fields.
- Schedule response + `interestWaived`, `interestCancelled`, `feesPaid`.
- `LoanController` waive-interest `@Operation` text.

## 3. Web (`web/`)
- `loan.service.ts`:
  - `waiveInterest(id, {reason, scope, waiveDate?, approvalReference?})`.
  - New `Loan` and installment fields.
  - Status union `PENDING|PAID|PARTIALLY_PAID|OVERDUE|WAIVED` (the web said `PARTIAL`).
- `loan-detail`:
  - Waive modal: scope radio, optional approval ref and date, accurate text, API error shown.
  - Schedule "Waived" column.
  - Credit Risk card shows interest waived and the modification discount.
  - WAIVED badge.
  - Overdue count and total fixed: not settled, `dueDate` < today, `totalDue − totalPaid`. Same fix in `loans-list.ts:95`.
- `accounting.service.ts` + `financial-activity-accounts.ts`: the two new activities and their labels.
- Specs: modal payload, badge, overdue total, labels.

## 4. Docs and housekeeping
- `docs/api-reference.html` and the identical `docs-site/static` copy, edited by an asserted script:
  - waive-interest body, scope, errors, fields;
  - posting matrix rows IWV, MOD, MODU, CLOSE, WOFF;
  - activities list (V60 1106/5005);
  - opening balances `scope=DEPOSITS|LOANS` with the LOANS line table and prerequisite;
  - loan-extension behaviour and error codes;
  - remove waivers from the "not posting yet" list.
- Postman (`docs/` + `docs-site/static/postman/`): waive body variants, LOANS preview and post.
- Regenerate `backend/docs/openapi-snapshot.yaml`.
- `CLAUDE.md`:
  - versions row;
  - GL module: remove waivers from "not posting yet", add a 2d bullet, opening-balances LOANS;
  - module 23 guards;
  - gap-analysis waive row.
- `cba-log.md` cont. 30: decisions with sources, files, pre-existing defects found, detection query, gate grep proof (tracked + untracked).
- Memory `project_gl_posting_decisions.md`: the 2d decisions and status.
- **Follow-ups (not here):**
  - PR 2e: reschedule / re-age schedule changes with modification accounting.
  - The `OVERDUE` status is never set, so the V9 *LoansInArrears* report and the `LoanRepaymentScheduleRepository` overdue queries return nothing.
  - Backend `CardServiceClient` sends no token.

## 5. Verification
- **Baseline first:** `cd backend && ./mvnw -o clean verify -Pfull-integration` on the branch before edits (main was 799 + card-service changes don't touch backend). Run it when the IDE is idle; rerun on a Testcontainers startup timeout.
- **Unit:**
  - `EffectiveInterestTest`: both numeric examples, a fee > 0 case, 0% rate, lazy = stored rate.
  - `LoanServiceTest` `WaiveInterest`: ACCRUED vs ALL, non-performing suspense, partly paid and paid-ahead, instalment becomes WAIVED and DPD drops, two waivers, date validation, NOTHING_TO_WAIVE, audit payload; also write-off/undo with the discount, quote and foreclosure with waived instalments.
  - `LoanAccrualServiceTest`: unwind release (incl. mid-period), to suspense when non-performing, closeOut.
  - `LoanImpairmentServiceTest`: exposure and receivable.
  - `LoanExtensionServiceTest`: guards, PENDING-only, loanId mismatch, audit.
- **Integration (real PostgreSQL):**
  - New `LoanWaiverIT`:
    - performing ALL: 1101 = 0, 5005 = accrued + L, 1106 = −L; repay → 1106 = 0;
    - accrue to maturity with Σ MODU = L;
    - non-performing: DR 2200, no MOD;
    - write-off/undo with 1106;
    - every journal balanced.
  - New `LoanOpeningBalanceIT`: legacy loan via JDBC → preview → post → reconciled after a waiver + repayment → 409.
  - `OpeningBalanceIT` changed to assert the clearing **delta**, because two scopes now share 3900.
  - `OpenApiSnapshotTest` regenerated.
- **Commands:** `./mvnw -o clean verify -Pfull-integration` (all green); `cd web && CI=true npx ng test --no-watch` + `npm run build`.
- **Then:** gate grep (tracked + untracked) → docs per §4 → PR → CI green → merge on the user's word.
