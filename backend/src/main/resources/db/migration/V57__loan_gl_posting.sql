-- ─────────────────────────────────────────────────────────────────────────────
-- V57 — Loan and loan-charge GL posting (GL posting PR 2a)
-- ─────────────────────────────────────────────────────────────────────────────
-- Loans are accounted for on an accrual basis (IFRS 9 §5.4.1): interest and fees are
-- recognised as income when earned or charged, against a receivable; cash received
-- clears the receivable. The sum of a loan's portfolio and receivable lines is its gross
-- carrying amount.
--
-- 1. Fees and penalties receivable, separate from interest receivable (1101), and a
--    penalty income account. Servicing fees are not part of the effective interest
--    rate (IFRS 9 B5.4.3) and are income when due (IFRS 15 §31); penalties are income
--    when charged. Added only where the V8 chart exists, like V54 and V56.

INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, FALSE
FROM (VALUES
    ('30000000-0000-0000-0000-000000000009', 'Loan Fees and Penalties Receivable', '1103', 'ASSET',
     '30000000-0000-0000-0000-000000000001', 'Loan fees and penalties charged and not yet paid.'),
    ('30000000-0000-0000-0000-000000000034', 'Penalty Income', '4004', 'INCOME',
     '30000000-0000-0000-0000-000000000030', 'Late-payment and other penalties charged on loans.')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

-- 2. Map the activities. INCOME_FEES was in the enum but never mapped; V8 seeds "Fee
--    Income" (4002) for it. Matched by id, so a bank's own account with the same code
--    is never picked up.
INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES
    ('ASSET_FEES_RECEIVABLE', '30000000-0000-0000-0000-000000000009'),
    ('INCOME_PENALTIES',      '30000000-0000-0000-0000-000000000034'),
    ('INCOME_FEES',           '30000000-0000-0000-0000-000000000032')
) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 3. Each charge definition may name its own income account. When empty, the loan
--    product's fee or penalty income link is used, then the activity mapping.
ALTER TABLE charge_definitions
    ADD COLUMN IF NOT EXISTS income_account_id UUID REFERENCES gl_accounts(id);

-- 4. The date a loan charge was recognised as income. NULL = not yet recognised (a fee
--    whose due date has not arrived); nothing can be paid against it until it is.
ALTER TABLE loan_charges
    ADD COLUMN IF NOT EXISTS income_recognized_on DATE;

-- 5. Teller cash received for a loan repayment has no deposit account, so the till
--    record points at the loan instead.
ALTER TABLE cash_transactions
    ADD COLUMN IF NOT EXISTS loan_id UUID REFERENCES loans(id);
CREATE INDEX IF NOT EXISTS idx_cash_txn_loan ON cash_transactions(loan_id);
