-- ─────────────────────────────────────────────────────────────────────────────
-- V58 — Loan interest accrual, days past due, non-performing suspense and origination
--       fee amortisation (GL posting PR 2b)
-- ─────────────────────────────────────────────────────────────────────────────
-- Interest is recognised daily as it is earned (IFRS 9 §5.4.1): DR interest receivable /
-- CR interest income. Origination fees are part of the effective interest rate (IFRS 9
-- B5.4.2): deferred at disbursement and released to interest income over the loan's life.
-- A loan 90 days or more past due is non-performing (CBN Prudential Guidelines; IFRS 9
-- B5.5.37 backstop): its unpaid accrued interest and penalties leave income for a
-- suspense account, later accruals go straight to suspense, and suspense becomes income
-- only as cash is received.
--
-- 1. GL accounts, only where the V8 chart exists (like V54, V56, V57).

INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, FALSE
FROM (VALUES
    ('30000000-0000-0000-0000-00000000000a', 'Deferred Loan Origination Fees', '1104', 'ASSET',
     '30000000-0000-0000-0000-000000000001',
     'Contra-asset (credit balance): origination fees received and not yet released to interest income. Part of the loans'' carrying amount.'),
    ('30000000-0000-0000-0000-000000000013', 'Interest and Fees in Suspense', '2200', 'LIABILITY',
     '30000000-0000-0000-0000-000000000010',
     'Interest and penalties accrued on non-performing loans and not yet received (CBN). Presented net of the receivables.')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES
    ('ASSET_DEFERRED_LOAN_FEES',     '30000000-0000-0000-0000-00000000000a'),
    ('LIABILITY_INCOME_IN_SUSPENSE', '30000000-0000-0000-0000-000000000013')
) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 2. Loan state. interest_accrued_through = the last day interest was accrued for (NULL:
--    never; the first run catches up from the disbursement date).
ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS interest_accrued_through DATE,
    ADD COLUMN IF NOT EXISTS days_past_due            INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS overdue_since            DATE,
    ADD COLUMN IF NOT EXISTS non_performing           BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS non_performing_since     DATE,
    ADD COLUMN IF NOT EXISTS interest_in_suspense     NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deferred_fee             NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deferred_fee_remaining   NUMERIC(19,4) NOT NULL DEFAULT 0;

-- 3. Per instalment: interest accrued so far, and its share of the origination fee.
ALTER TABLE loan_repayment_schedule
    ADD COLUMN IF NOT EXISTS interest_accrued NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fee_amortisation NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fee_amortised    NUMERIC(19,4) NOT NULL DEFAULT 0;

-- 4. A penalty recognised while the loan was non-performing sits in suspense, not income.
ALTER TABLE loan_charges
    ADD COLUMN IF NOT EXISTS suspended BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_loans_status_accrual ON loans(status, interest_accrued_through);
