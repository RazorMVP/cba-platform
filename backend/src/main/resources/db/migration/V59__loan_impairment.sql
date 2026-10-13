-- ─────────────────────────────────────────────────────────────────────────────
-- V59 — Loan impairment (IFRS 9 expected credit losses), write-off against the
--       allowance, recoveries, and the CBN regulatory risk reserve (GL posting PR 2c)
-- ─────────────────────────────────────────────────────────────────────────────
-- Every open loan carries a loss allowance = EAD × PD × LGD (IFRS 9 §5.5.3, §5.5.5):
-- stage 1 (≤ 30 days past due) 12-month losses, stage 2 (> 30, §5.5.11) and stage 3
-- (> 90 or non-performing, B5.5.37) lifetime losses. Changes go to profit or loss as an
-- impairment gain or loss (§5.5.8). A write-off reduces the gross carrying amount
-- against the allowance (§5.4.4); cash recovered later is an impairment gain.
-- CBN Prudential Guidelines: where the prudential provision exceeds the IFRS allowance,
-- the excess moves from retained earnings to a non-distributable regulatory risk
-- reserve (equity, never profit or loss), and back when it falls.
--
-- 1. GL accounts, only where the V8 chart exists (like V54, V56, V57, V58).

INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, FALSE
FROM (VALUES
    ('30000000-0000-0000-0000-00000000000b', 'Loan Loss Allowance', '1105', 'ASSET',
     '30000000-0000-0000-0000-000000000001',
     'Contra-asset (credit balance): IFRS 9 expected credit losses on loans. Loans are presented net of it.'),
    ('30000000-0000-0000-0000-000000000044', 'Credit Impairment Charge', '5004', 'EXPENSE',
     '30000000-0000-0000-0000-000000000040',
     'Impairment losses on loans, net of reversals and recoveries of amounts written off (IFRS 9 §5.5.8; IAS 1 §82(ba)).'),
    ('30000000-0000-0000-0000-000000000022', 'Regulatory Risk Reserve', '3002', 'EQUITY',
     '30000000-0000-0000-0000-000000000020',
     'Non-distributable reserve: CBN prudential provisions in excess of the IFRS 9 loss allowance, transferred from retained earnings.')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES
    ('ASSET_LOAN_LOSS_ALLOWANCE',      '30000000-0000-0000-0000-00000000000b'),
    ('EXPENSE_LOAN_LOSS_PROVISION',    '30000000-0000-0000-0000-000000000044'),
    ('EQUITY_RETAINED_EARNINGS',       '30000000-0000-0000-0000-000000000021'),
    ('EQUITY_REGULATORY_RISK_RESERVE', '30000000-0000-0000-0000-000000000022')
) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 2. Loan state: ECL stage and allowance, the CBN prudential provision, write-off details.
ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS ecl_stage                INTEGER,
    ADD COLUMN IF NOT EXISTS loss_allowance           NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ecl_measured_on          DATE,
    ADD COLUMN IF NOT EXISTS prudential_provision     NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fully_provided_since     DATE,
    ADD COLUMN IF NOT EXISTS write_off_approval_ref   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS written_off_amount       NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS recovered_amount         NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS write_off_transaction_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS write_off_snapshot       JSONB;

-- 3. Loss given default per product. 45% is the Basel foundation-IRB value for senior
--    unsecured exposures: a placeholder until the risk team supplies product LGDs.
ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS lgd_percentage NUMERIC(5,2) NOT NULL DEFAULT 45.00;

-- 4. Probability of default by stage and days-past-due band (risk-team table). Stage 1
--    is a 12-month PD, stages 2 and 3 lifetime PDs. Placeholder values.
CREATE TABLE IF NOT EXISTS ecl_pd_rates (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    stage         INTEGER       NOT NULL CHECK (stage BETWEEN 1 AND 3),
    min_dpd       INTEGER       NOT NULL CHECK (min_dpd >= 0),
    max_dpd       INTEGER       NOT NULL,
    pd_percentage NUMERIC(7,4)  NOT NULL CHECK (pd_percentage BETWEEN 0 AND 100),
    description   VARCHAR(200),
    tenant_id     UUID,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (max_dpd >= min_dpd)
);
CREATE INDEX IF NOT EXISTS idx_ecl_pd_rates_stage ON ecl_pd_rates(stage, min_dpd);

INSERT INTO ecl_pd_rates (stage, min_dpd, max_dpd, pd_percentage, description)
SELECT v.stage, v.min_dpd, v.max_dpd, v.pd, v.description
FROM (VALUES
    (1, 0,  30,    2.0000::numeric, 'Stage 1 — performing, 12-month PD (placeholder)'),
    (2, 31, 90,   20.0000::numeric, 'Stage 2 — significant increase in credit risk, lifetime PD (placeholder)'),
    (3, 91, 99999, 100.0000::numeric, 'Stage 3 — credit-impaired (in default)')
) AS v(stage, min_dpd, max_dpd, pd, description)
WHERE NOT EXISTS (SELECT 1 FROM ecl_pd_rates);

-- 5. CBN prudential provisioning bands (days past due), in the provisioning criteria
--    tables. Performing loans carry a general provision; for non-performing loans the
--    impairment job also provides 100% of principal more than 90 days overdue.
INSERT INTO provisioning_criteria (id, criteria_name, is_active, created_by)
VALUES ('40000000-0000-0000-0000-000000000059', 'CBN Prudential', TRUE, 'system')
ON CONFLICT DO NOTHING;

INSERT INTO provisioning_criteria_definitions (criteria_id, category_name, min_age, max_age, provision_percentage)
SELECT '40000000-0000-0000-0000-000000000059'::uuid, v.category, v.min_age, v.max_age, v.pct
FROM (VALUES
    ('PERFORMING',   0,   90,    2.00::numeric),
    ('SUB_STANDARD', 91,  180,  10.00::numeric),
    ('DOUBTFUL',     181, 360,  50.00::numeric),
    ('LOST',         361, 99999, 100.00::numeric)
) AS v(category, min_age, max_age, pct)
WHERE EXISTS (SELECT 1 FROM provisioning_criteria WHERE id = '40000000-0000-0000-0000-000000000059')
  AND NOT EXISTS (SELECT 1 FROM provisioning_criteria_definitions
                  WHERE criteria_id = '40000000-0000-0000-0000-000000000059');

-- 6. CBN write-off rule: a loan may be written off only once it has been fully provided
--    for at least a year. Admins can switch the check off (PUT /api/v1/configurations).
INSERT INTO global_configurations (name, boolean_value, is_enabled, description)
VALUES ('enforce-cbn-write-off-rules', TRUE, TRUE,
        'When enabled, a loan can be written off only after its CBN prudential provision has covered the principal for at least one year. A board-approval reference is always required.')
ON CONFLICT (name) DO NOTHING;
