-- ─────────────────────────────────────────────────────────────────────────────
-- V60 — Loan interest waivers (GL posting PR 2d)
-- ─────────────────────────────────────────────────────────────────────────────
-- Waiving interest that has accrued but is unpaid derecognises part of the receivable
-- (IFRS 9 §3.2.3(a); IFRS Interpretations Committee, Lessor Forgiveness of Lease
-- Payments, Oct 2022, by analogy): what sits in suspense was never income, so it just
-- leaves suspense (CBN Prudential Guidelines §15.8); the rest is a loss. Waiving future
-- interest modifies the contractual cash flows (§5.4.3): the gross carrying amount falls
-- to the present value of the new cash flows at the original effective interest rate,
-- the difference is a loss now, and the discount unwinds into interest income over the
-- remaining term. Both losses go to one expense line (5005) so the amount can be
-- disclosed (IFRS 7 §35J).
--
-- 1. GL accounts, only where the V8 chart exists (like V54, V56–V59).

INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, FALSE
FROM (VALUES
    ('30000000-0000-0000-0000-00000000000c', 'Loan Modification Discount', '1106', 'ASSET',
     '30000000-0000-0000-0000-000000000001',
     'Contra-asset (credit balance): IFRS 9 §5.4.3 modification losses on loans not yet unwound into interest income.'),
    ('30000000-0000-0000-0000-000000000045', 'Loan Modification and Waiver Losses', '5005', 'EXPENSE',
     '30000000-0000-0000-0000-000000000040',
     'Losses on interest waived: accrued interest derecognised (IFRS 9 §3.2.3) and future interest given up (§5.4.3).')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES
    ('ASSET_LOAN_MODIFICATION_DISCOUNT', '30000000-0000-0000-0000-00000000000c'),
    ('EXPENSE_LOAN_MODIFICATION_LOSS',   '30000000-0000-0000-0000-000000000045')
) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 2. The loan's original effective daily rate (null until first needed: loans disbursed
--    before V60 get it computed from their contractual schedule) and the modification
--    discount not yet unwound.
ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS effective_daily_rate             DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS modification_discount_remaining  NUMERIC(19,4) NOT NULL DEFAULT 0;

-- 3. Per instalment: accrued interest derecognised (stays inside interest_due), future
--    interest removed from interest_due, and the period's share of the discount unwind.
ALTER TABLE loan_repayment_schedule
    ADD COLUMN IF NOT EXISTS interest_waived       NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS interest_cancelled    NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS modification_unwind   NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS modification_unwound  NUMERIC(19,4) NOT NULL DEFAULT 0;

-- 4. Loans waived before V60 are not repaired here. The old waiver set interest_paid to
--    interest_due with no cash and posted nothing; every formula now uses paid + waived
--    together, so those rows behave like a waiver, but interest the nightly accrual
--    booked on the "waived" future interest is still on 1101. Find them with:
--      SELECT l.loan_account_number, a.changed_at, a.new_values->>'totalWaived'
--      FROM audit_log a JOIN loans l ON l.id::text = a.entity_id
--      WHERE a.entity_type = 'LOAN' AND a.action = 'WAIVE_INTEREST' AND NOT (a.new_values ? 'scope');
--    (or schedule rows with total_paid < principal_paid + interest_paid + fees_paid),
--    and correct 1101 by manual journal against 5005.
