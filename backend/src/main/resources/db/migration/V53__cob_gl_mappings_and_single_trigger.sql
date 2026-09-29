-- ─────────────────────────────────────────────────────────────────────────────
-- V53 — Close-of-Business: GL mappings for interest, and one sequenced trigger
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. V8 seeded financial activities under names that were never FinancialActivity
--    enum constants (INTEREST_INCOME vs INCOME_INTEREST, ...). JPA could not load
--    any row, so GET /api/v1/financialactivityaccounts returned 500 and no activity
--    could be resolved for posting. Rename each to its enum constant. If an admin
--    has since created the correctly named row, the legacy row is unloadable and
--    superseded, so drop it instead of colliding on the UNIQUE constraint.
WITH renames(old_name, new_name) AS (VALUES
    ('ASSET_TRANSFER',      'ASSET_FUND_SOURCE'),
    ('LIABILITY_TRANSFER',  'LIABILITY_SAVINGS_CONTROL'),
    ('CASH_AT_TELLER',      'ASSET_CASH_AT_TELLER'),
    ('LOAN_PORTFOLIO',      'ASSET_LOAN_PORTFOLIO'),
    ('INTEREST_RECEIVABLE', 'ASSET_INTEREST_RECEIVABLE'),
    ('INTEREST_INCOME',     'INCOME_INTEREST'),
    ('WRITE_OFF_EXPENSE',   'EXPENSE_WRITE_OFF'))
DELETE FROM financial_activity_accounts legacy
USING renames r
WHERE legacy.financial_activity = r.old_name
  AND EXISTS (SELECT 1 FROM financial_activity_accounts f WHERE f.financial_activity = r.new_name);

WITH renames(old_name, new_name) AS (VALUES
    ('ASSET_TRANSFER',      'ASSET_FUND_SOURCE'),
    ('LIABILITY_TRANSFER',  'LIABILITY_SAVINGS_CONTROL'),
    ('CASH_AT_TELLER',      'ASSET_CASH_AT_TELLER'),
    ('LOAN_PORTFOLIO',      'ASSET_LOAN_PORTFOLIO'),
    ('INTEREST_RECEIVABLE', 'ASSET_INTEREST_RECEIVABLE'),
    ('INTEREST_INCOME',     'INCOME_INTEREST'),
    ('WRITE_OFF_EXPENSE',   'EXPENSE_WRITE_OFF'))
UPDATE financial_activity_accounts f
SET financial_activity = r.new_name, updated_at = now()
FROM renames r
WHERE f.financial_activity = r.old_name;

-- 2. Interest credited to savings is an expense of the bank:
--    DR Interest Expense (5002) / CR Customer Deposits (LIABILITY_SAVINGS_CONTROL).
--    Only mapped where the seeded chart of accounts exists; a bank with its own chart
--    maps it on the Financial Activity Accounts screen. Until it is mapped, the
--    interest accrual job fails rather than crediting interest with no GL entry.
INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT 'EXPENSE_INTEREST_ON_SAVINGS', id FROM gl_accounts WHERE gl_code = '5002'
ON CONFLICT (financial_activity) DO NOTHING;

-- 3. The four CoB jobs used to have one Quartz trigger each (23:55/56/57/59), so
--    they ran independently and could overlap. They now run in sequence from a
--    single trigger (CobSchedulerConfig). Quartz persists triggers in these tables
--    and would keep firing the old ones, so remove them. No-op on a fresh database.
DELETE FROM qrtz_cron_triggers
WHERE trigger_group = 'cob'
  AND trigger_name IN ('standingOrderTrigger', 'dormancyTrigger', 'interestAccrualTrigger', 'arrearsTrigger');

DELETE FROM qrtz_triggers
WHERE trigger_group = 'cob'
  AND trigger_name IN ('standingOrderTrigger', 'dormancyTrigger', 'interestAccrualTrigger', 'arrearsTrigger');

DELETE FROM qrtz_job_details
WHERE job_group = 'cob'
  AND job_name IN ('standingOrderExecution', 'dormancyClassification', 'interestAccrual', 'arrearsClassification');
