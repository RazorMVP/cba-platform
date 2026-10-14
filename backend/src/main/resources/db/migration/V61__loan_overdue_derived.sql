-- ─────────────────────────────────────────────────────────────────────────────
-- V61 — Report categories loadable; LoansInArrears: overdue derived from the due date
-- ─────────────────────────────────────────────────────────────────────────────
-- The V9 report joined instalments with status 'OVERDUE', which nothing ever stores
-- (overdue depends on today's date), so it always returned no rows. It also aged a loan
-- by its most recent missed instalment instead of its oldest.
--
-- Overdue now means: due before today and not settled (PAID, or the rest WAIVED); a
-- partly paid instalment is still overdue. The loan is aged by its oldest such
-- instalment, as days past due is (LoanAccrualService.updateArrears). Open loans only:
-- a written-off loan's schedule is legal debt being recovered, not arrears.
-- Buckets 1-30 / 31-60 / 61-90 / 90+ days (a loan due yesterday is 1 day past due).

-- 1. Report categories. V9 seeded 'Loans', 'Savings', 'Teller', 'Customers', 'Accounting'
--    and 'Products' (V29 defaults 'General'), but Report.category is an enum
--    (LOAN | SAVINGS | TELLER | CUSTOMER | ACCOUNTING | MIXED) mapped by name, so loading
--    any seeded report failed ("No enum constant ... Category.Loans"): none of the seven
--    could be listed or run.
UPDATE reports
SET category = CASE category
        WHEN 'Loans'      THEN 'LOAN'
        WHEN 'Products'   THEN 'LOAN'      -- LoanProductSummary
        WHEN 'Savings'    THEN 'SAVINGS'
        WHEN 'Teller'     THEN 'TELLER'
        WHEN 'Customers'  THEN 'CUSTOMER'
        WHEN 'Accounting' THEN 'ACCOUNTING'
        ELSE 'MIXED'
    END
WHERE category NOT IN ('LOAN', 'SAVINGS', 'TELLER', 'CUSTOMER', 'ACCOUNTING', 'MIXED');

ALTER TABLE reports ALTER COLUMN category SET DEFAULT 'MIXED';

-- 2. The LoansInArrears query.
UPDATE reports
SET report_sql = $$SELECT l.loan_account_number, l.customer_id, l.outstanding_balance,
       CURRENT_DATE - MIN(lrs.due_date) AS days_overdue,
       SUM(lrs.total_due - lrs.total_paid) AS amount_overdue,
       CASE
         WHEN CURRENT_DATE - MIN(lrs.due_date) <= 30 THEN '1-30 days'
         WHEN CURRENT_DATE - MIN(lrs.due_date) <= 60 THEN '31-60 days'
         WHEN CURRENT_DATE - MIN(lrs.due_date) <= 90 THEN '61-90 days'
         ELSE '90+ days'
       END AS ageing_bucket
FROM loans l
JOIN loan_repayment_schedule lrs ON lrs.loan_id = l.id
 AND lrs.due_date < CURRENT_DATE
 AND lrs.status NOT IN ('PAID', 'WAIVED')
WHERE l.status IN ('ACTIVE', 'IN_ARREARS')
GROUP BY l.id, l.loan_account_number, l.customer_id, l.outstanding_balance
ORDER BY days_overdue DESC$$,
    description = 'Loans with instalments past due and not settled, aged by the oldest (1-30/31-60/61-90/90+ days)',
    updated_at  = now()
WHERE report_name = 'LoansInArrears';
