> **Status:** merged — PR #145, merge commit `73d209f`. Approved 2026-10-13.  
> Recovered verbatim from the session transcript (the approved `ExitPlanMode` plan); the body below is unedited.

# GL posting PR 2c — IFRS 9 loss allowance, write-off, recoveries, CBN regulatory risk reserve

## Context
PR 2a/2b (merged #135, #141) made loan money flows, accrual, days past due and 90-day suspense post to the GL. Three things still post **nothing**: loan write-off, undo write-off and any credit-loss provision (`LoanService.writeOffLoan:326`, `undoWriteOff:346`; `EXPENSE_LOAN_LOSS_PROVISION` exists in the enum but is unmapped and unused). That breaks the standing rule that every money movement posts a balanced journal, and the GL has no IFRS 9 loss allowance or CBN regulatory risk reserve.

User decisions (2026-10-11, "1a 2a 3a 4a 5a") on top of PR 2 decisions 4b/6a:
1a keep CBN suspense for stage-3 interest (no IFRS 5.4.1(b) net-interest change) · 2a reuse provisioning criteria for CBN bands · 3a reserve posts on month-end business dates + manual run · 4a write-off only stage 3 + board-approval reference + CBN "fully provided ≥1 year" rule enforced behind a global config flag · 5a recoveries credit the impairment charge; undo = exact reversing journal dated on the write-off date (closed-period check applies).

Standards: IFRS 9 §5.5.3/5.5.5 (lifetime vs 12-month), §5.5.11 (>30 dpd → stage 2), B5.5.37 (>90 dpd default), §5.5.8 (impairment gain/loss in P&L), §5.4.4 (write-off = derecognition, reduce gross carrying amount); IAS 1 §82(ba) (impairment line incl. reversals); CBN Prudential Guidelines (performing 2%, sub-standard 91–180 10%, doubtful 181–360 50%, lost >360 100%, overdue principal >90 100%; write-off when fully provided ≥1 year + board approval; prudential > IFRS ⇒ difference from general reserve to non-distributable regulatory risk reserve, reversed when it falls); CBN circular BSD/DIR/GEN/LAB/11/027 (2018).

Branch `feat/gl-posting-loans-2c` off `main` (`f780d9a`), one PR.

## 1. Migration `V59__loan_impairment.sql`
Follow the V57/V58 multi-row pattern (insert only when the header exists; `ON CONFLICT DO NOTHING`).
- GL accounts: **1105 Loan Loss Allowance** (ASSET contra, id `30000000-0000-0000-0000-00000000000b`, parent `…0001`); **5004 Credit Impairment Charge** (EXPENSE, `…0044`, parent `…0040`); **3002 Regulatory Risk Reserve** (EQUITY, non-distributable, `…0022`, parent `…0020`). All `manual_entries_allowed = FALSE`.
- Activity mappings: `ASSET_LOAN_LOSS_ALLOWANCE`→1105, `EXPENSE_LOAN_LOSS_PROVISION`→5004 (existing enum, first mapping), `EQUITY_RETAINED_EARNINGS`→3001, `EQUITY_REGULATORY_RISK_RESERVE`→3002 (names ≤ 50 chars: column is VARCHAR(50)).
- `loans`: `ecl_stage SMALLINT`, `loss_allowance NUMERIC(19,4) NOT NULL DEFAULT 0`, `ecl_measured_on DATE`, `prudential_provision NUMERIC(19,4) NOT NULL DEFAULT 0`, `fully_provided_since DATE`, `write_off_approval_ref VARCHAR(100)`, `written_off_amount NUMERIC(19,4)`, `recovered_amount NUMERIC(19,4) NOT NULL DEFAULT 0`, `write_off_transaction_id VARCHAR(64)`, `write_off_snapshot JSONB`.
- `loan_products.lgd_percentage NUMERIC(5,2) NOT NULL DEFAULT 45.00` (Basel F-IRB senior unsecured placeholder until the risk team supplies values).
- New `ecl_pd_rates` (id, stage SMALLINT CHECK 1–3, min_dpd INT, max_dpd INT, pd_percentage NUMERIC(7,4), description, tenant_id, version, created_at, updated_at). Seed placeholders: stage 1 0–30 2%, stage 2 31–90 20%, stage 3 91–99999 100%.
- Seed provisioning criteria **"CBN Prudential"** (active): PERFORMING 0–90 2.00, SUB_STANDARD 91–180 10.00, DOUBTFUL 181–360 50.00, LOST 361–99999 100.00 (GL account ids null — the reserve posts to equity, not these columns).
- `global_configurations`: `enforce-cbn-write-off-rules` boolean TRUE enabled (V40 INSERT pattern).

## 2. Backend

### Entities / enums
- `accounting/FinancialActivityAccount.FinancialActivity`: add `ASSET_LOAN_LOSS_ALLOWANCE`, `EQUITY_RETAINED_EARNINGS`, `EQUITY_REGULATORY_RISK_RESERVE`. (`AccountGlPostingTest:54` maps all values automatically.)
- `accounting/JournalEntry.EntityType`: add `REGULATORY_RESERVE` (≤ 20 chars).
- `loan/Loan.java`: fields for the V59 columns; snapshot as `@JdbcTypeCode(SqlTypes.JSON) Map<String, BigDecimal>` (same pattern as `Hook.events`).
- `product/LoanProduct.java` + `LoanProductRequest/Response` + `ProductService`: `lgdPercentage` (validate 0–100, default 45).
- New `loan/EclPdRate` entity + `EclPdRateRepository`.

### `loan/LoanImpairmentService` (new, `@Service`)
- `measure(UUID loanId, LocalDate date)` — `REQUIRES_NEW`, ACTIVE/IN_ARREARS only; delegates to `remeasure(Loan, date)` (`MANDATORY`, also used by write-off).
- **Stage**: 3 if `nonPerforming` or dpd > 90; 2 if dpd > 30; else 1.
- **EAD** (gross carrying amount, from the entity — same quantities 2b keeps per loan): `outstandingBalance` + Σ(`interestAccrued` − `interestPaid`) + recognised unpaid charges outstanding − `deferredFeeRemaining` − (`interestInSuspense` + suspended charges outstanding); floor 0. Charge filter copied from `LoanService.quote` (`incomeRecognizedOn != null && !paid && !waived`).
- **PD** from `ecl_pd_rates` (stage + dpd within band, else first row for the stage, else `ECL_PD_NOT_CONFIGURED`); **LGD** = `product.lgdPercentage`.
- **ECL** = EAD × PD × LGD (scale 4, HALF_UP). Delta vs `lossAllowance`: increase → DR impairment / CR 1105; decrease → DR 1105 / CR impairment (gain). Impairment account = `EXPENSE_LOAN_LOSS_PROVISION`, added last via `AccountGlPosting.addProfitOrLoss` (foreign-currency loans go through the FX position). Ref `ECL-…`.
- **Prudential provision (CBN)** per loan, from the single active provisioning criteria (none → `PROVISIONING_CRITERIA_NOT_CONFIGURED`, more than one → `PROVISIONING_CRITERIA_AMBIGUOUS`): band by dpd; dpd ≤ 90 → pct × principal outstanding; dpd > 90 → 100% × principal overdue (instalments due before date) + pct × principal not yet due. Store `prudential_provision`; set `fully_provided_since` when it first reaches the principal outstanding, clear it when it drops below.
- `release(Loan, date)` (`MANDATORY`): on payoff/foreclosure posts the remaining allowance back (DR 1105 / CR impairment), zeroes allowance/stage/prudential.

### `loan/LoanGlPosting` additions (same helpers: `addDebit/addCredit`, P&L last, `reference(kind, loan)`)
- `postImpairment(loan, delta, date)` — ECL.
- `postWriteOff(loan, WriteOffAmounts, date)` — WOFF: CR 1100 principal, CR/DR 1101 signed interest, CR 1103 fees; DR 1104 deferred fee; DR 2200 suspense; DR 1105 allowance; difference (gross − allowance) DR impairment if positive, CR if negative. Shortfall account = product `writeOffAccount` link if set, else `EXPENSE_LOAN_LOSS_PROVISION` (`writeOffAccount` finally gets read).
- `postRecovery(loan, source, amount, date)` — RECV: DR source (reuse `collect`) / CR impairment.
- `postReversal(loan, transactionId, date)` — WOFFREV: every line of the original journal (`JournalEntryRepository.findByTransactionIdOrderByIdAsc`) flipped, same currency/position tag.

### `loan/LoanAccrualService`
Extract `bringCurrent(Loan, date)` (`MANDATORY`: updateArrears → classify → recogniseDueCharges → accrueThrough) from `runCloseOfBusiness` so write-off can bring a loan current before writing it off.

### `loan/LoanService`
- **writeOffLoan**: ACTIVE/IN_ARREARS; date not future, not before disbursement; `approvalReference` required (`WRITE_OFF_APPROVAL_REQUIRED`); `bringCurrent` + `remeasure`; stage 3 required (`WRITE_OFF_NOT_CREDIT_IMPAIRED`); if `enforce-cbn-write-off-rules` enabled, `fully_provided_since` ≤ date − 1 year (`WRITE_OFF_CBN_RULES_NOT_MET`); compute amounts, snapshot them, post WOFF, zero allowance/suspense/deferred fee/outstanding, set write-off fields; audit with the real old status (fixes hard-coded `ACTIVE`).
- **undoWriteOff**: reject if `recoveredAmount > 0` (`WRITE_OFF_HAS_RECOVERIES`); post WOFFREV dated `writtenOffOn` (GL closure guard rejects closed periods); restore snapshot fields; `updateArrears(loan, today)` sets status; clear write-off fields.
- **makeRepayment** on WRITTEN_OFF → recovery: cap at contractual outstanding (`scheduledOutstanding`), allocate to schedule (legal balance), post RECV, `recoveredAmount += amount`; no accrual, no receivable lines.
- Full repayment / foreclosure: call `loanImpairmentService.release` next to `closeOut`.
- `toResponse`/`LoanResponse`: add `eclStage`, `lossAllowance`, `prudentialProvision`, `writtenOffOn`, `writeOffReason`, `writeOffApprovalReference`, `writtenOffAmount`, `recoveredAmount`.
- `WriteOffRequest(writeOffDate, reason, approvalReference)` — keep the 2-arg constructor.

### `charge/ChargeService`
`payLoanCharge` / `waiveLoanCharge` reject WRITTEN_OFF loans (`LOAN_WRITTEN_OFF`): receivables are derecognised; recoveries go through repayments.

### `accounting/RegulatoryReserveService` (new)
`post(date, manual)`: skip unless `manual` or date is the last day of its month; advisory lock `regulatory-reserve` (OpeningBalanceService pattern); totals over ACTIVE/IN_ARREARS loans of `prudentialProvision` and `lossAllowance` converted to the functional currency (`ExchangeRateService.convert`); target = max(0, prudential − ECL); current = 3002 credit balance (JDBC, flush first); delta: DR 3001 / CR 3002 or the reverse; ref `RRR-<date>`, `EntityType.REGULATORY_RESERVE`. Rerun is a no-op.

### EclController (new, `/api/v1/ecl`)
- `GET /pd-rates` (ADMIN, TELLER), `PUT /pd-rates` (ADMIN, replace-all; validate stages 1–3 present, 0 ≤ PD ≤ 100, min ≤ max, no overlap per stage).
- `GET /summary` (ADMIN, TELLER): per currency × stage loans/exposure/allowance; prudential total, ECL total, reserve target and current balance (functional currency).

### CoB
- `cob/LoanImpairmentJob` (bean `loanImpairmentBatchJob`, job `loanImpairmentJob`) — clone of `LoanAccrualJob` (per-loan `REQUIRES_NEW`, failures counted, fails at end).
- `cob/RegulatoryReserveJob` (bean `regulatoryRiskReserveBatchJob`, job `regulatoryRiskReserveJob`) — tasklet like `FxRevaluationJob`; reads job parameter `manual`.
- `CobJobDefinition`: order … LOAN_ACCRUAL → **LOAN_IMPAIRMENT** → **REGULATORY_RESERVE** → FX_REVALUATION; add `manualParameters(date)` (adds `manual=true`), used by `CobJobService.runNow`.
- `CobController` `@Operation` job-name list (stale, lists 4) updated.

## 3. Web (`web/`)
- `loan.service.ts`: `Loan` gets the new fields; `writeOff(id, reason, approvalReference, writeOffDate?)`.
- `loan-detail`: write-off modal adds required board-approval reference, replaces the wrong "irreversible" text, shows the API error message (`apiMessage`); a "Credit risk" card on the summary tab (days past due, stage, loss allowance, interest in suspense, non-performing); repayment allowed on WRITTEN_OFF as "Record Recovery".
- `provisioning.ts`: CBN default bands; new "ECL PD rates" card (`getPdRates`/`updatePdRates` in `accounting.service.ts`).
- Loan product detail: LGD % field (product.service types).
- `accounting.service.ts` `FinancialActivityType` + `financial-activity-accounts.ts` labels: the three new activities.
- `cob-scheduler.html:16` sequence text (also fixes missing loan accrual).
- Specs: `loan.service.spec`, `loan-detail.spec` (approval field, canRepay on WRITTEN_OFF), `provisioning.spec` (4 CBN bands + PD rates), product spec as touched.

## 4. Docs and housekeeping
- `docs/api-reference.html` (scripted, each replacement asserted once, like `apiref_pr2b.py`): new POST write-off section; undo + recovery text; loan response fields; CoB = 8 jobs + sequence (fixes "five"/"six" mismatch); posting rows ECL/WOFF/WOFFREV/RECV/RRR; remove write-off/allowance from "do not post yet"; activities list (+ fix "the 16 listed"); provisioning = CBN bands; new `/ecl` endpoints; product `lgdPercentage`; matrix row `?command=writeOff` → `/write-off`.
- Postman (`ensure_ascii=True`, no trailing newline): write-off body + code samples with `approvalReference`; new "ECL" folder (3 requests); triggers for `loanAccrualJob`, `loanImpairmentJob`, `regulatoryRiskReserveJob`; provisioning create body = CBN bands.
- Copy both to `docs-site/static/`; regenerate `backend/docs/openapi-snapshot.yaml`.
- `CLAUDE.md`: versions row, GL module (remove write-off/ECL from "Not posting yet", add 2c bullet), CoB sequence + valid job names, Module 43 bands. `cba-log.md` cont. 26 with gate grep proof. Memory `project_gl_posting_decisions.md`: 2c decisions + status.
- Known, left for 2d: `waiveInterest` posts nothing; `LoanExtensionService` has no status guard.

## 5. Verification
- Unit: `LoanImpairmentServiceTest` (stage mapping, EAD, PD lookup/fallback/missing, delta up/down, prudential performing vs NPL split, `fully_provided_since` set/clear), `RegulatoryReserveServiceTest` (non-month-end skip, manual, both directions, FX conversion), `EclParameterService` validation, `LoanServiceTest` write-off (approval, stage 3, CBN flag on/off, WOFF amounts, snapshot, real old status) / undo (reversal, recoveries block) / recovery, `ChargeServiceTest` WRITTEN_OFF block.
- Integration `LoanImpairmentIT` (real PG): disburse → CoB +40d stage 2 allowance → +95d stage 3 → write-off (flag off) → per-loan 1100/1101/1103/1104/2200/1105 net zero → recovery credits 5004 → undo blocked; second loan undo restores balances; forced reserve job moves 3001→3002 by prudential − ECL; every journal balanced. `CobJobsIT` job lists = 8; unmapped-activity failure test for the impairment job.
- Commands: `cd backend && ./mvnw clean verify -Pfull-integration` (expect 783 + new, all green); `cd web && CI=true npx ng test --no-watch` and `npm run build`; OpenAPI snapshot `-Dupdate.api.snapshot=true`.
- Session gate grep (endpoint/param lines will print → docs + Postman updated), then PR; merge only on user's word with CI green.
