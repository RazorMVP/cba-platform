-- V20: Extend loan_products and deposit_products to full Mifos parity
-- Adds: short_name, fund linkage, repayment schedule config, interest config,
--       grace periods, arrears tolerance, attribute overrides, GL account linkages,
--       product-charges join tables.

-- ── loan_products — new columns ──────────────────────────────────────────────

ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS short_name                         VARCHAR(4)        UNIQUE,
    ADD COLUMN IF NOT EXISTS fund_id                            UUID              REFERENCES funds(id),
    ADD COLUMN IF NOT EXISTS default_principal                  NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS installment_amount_in_multiples_of INTEGER,

    -- Interest config
    ADD COLUMN IF NOT EXISTS interest_rate_frequency_type       VARCHAR(20)       NOT NULL DEFAULT 'PER_YEAR',
    ADD COLUMN IF NOT EXISTS interest_type                      VARCHAR(20)       NOT NULL DEFAULT 'DECLINING_BALANCE',
    ADD COLUMN IF NOT EXISTS amortization_type                  VARCHAR(30)       NOT NULL DEFAULT 'EQUAL_INSTALLMENTS',
    ADD COLUMN IF NOT EXISTS interest_calculation_period_type   VARCHAR(30)       NOT NULL DEFAULT 'SAME_AS_REPAYMENT_PERIOD',
    ADD COLUMN IF NOT EXISTS days_in_year_type                  VARCHAR(20)       NOT NULL DEFAULT 'ACTUAL',
    ADD COLUMN IF NOT EXISTS days_in_month_type                 VARCHAR(20)       NOT NULL DEFAULT 'ACTUAL',

    -- Repayment schedule
    ADD COLUMN IF NOT EXISTS number_of_repayments               INTEGER           NOT NULL DEFAULT 12,
    ADD COLUMN IF NOT EXISTS repayment_every                    INTEGER           NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS repayment_frequency_type           VARCHAR(20)       NOT NULL DEFAULT 'MONTHS',

    -- Grace periods
    ADD COLUMN IF NOT EXISTS grace_on_principal_payment         INTEGER,
    ADD COLUMN IF NOT EXISTS grace_on_interest_payment          INTEGER,
    ADD COLUMN IF NOT EXISTS grace_on_interest_charged          INTEGER,
    ADD COLUMN IF NOT EXISTS grace_on_arrears_ageing            INTEGER,
    ADD COLUMN IF NOT EXISTS in_arrears_tolerance               NUMERIC(19,4),

    -- Attribute overrides (embedded, all default true)
    ADD COLUMN IF NOT EXISTS allow_override_amortization_type       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allow_override_interest_type            BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allow_override_repayment_every          BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allow_override_repayment_frequency      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allow_override_repayment_strategy       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allow_override_grace_principal_interest BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allow_override_grace_interest_charged   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS allow_override_interest_rate            BOOLEAN NOT NULL DEFAULT TRUE,

    -- GL account linkages
    ADD COLUMN IF NOT EXISTS fund_source_account_id                  UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS loan_portfolio_account_id               UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS transfers_in_suspense_account_id        UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS interest_on_loan_account_id             UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS income_from_fees_account_id             UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS income_from_penalties_account_id        UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS write_off_account_id                    UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS overpayment_liability_account_id        UUID REFERENCES gl_accounts(id);

-- Backfill short_name for any existing rows (generate from name, deduplicate with row-number suffix)
WITH ranked AS (
    SELECT id,
           UPPER(SUBSTRING(REGEXP_REPLACE(name, '[^A-Za-z0-9]', '', 'g'), 1, 4)) AS base_code,
           ROW_NUMBER() OVER (
               PARTITION BY UPPER(SUBSTRING(REGEXP_REPLACE(name, '[^A-Za-z0-9]', '', 'g'), 1, 4))
               ORDER BY id
           ) AS rn
    FROM loan_products
    WHERE short_name IS NULL
)
UPDATE loan_products lp
SET short_name = CASE WHEN r.rn = 1 THEN r.base_code
                      ELSE SUBSTRING(r.base_code, 1, 3) || CAST(r.rn AS VARCHAR)
                 END
FROM ranked r
WHERE lp.id = r.id;

ALTER TABLE loan_products
    ALTER COLUMN short_name SET NOT NULL;

-- ── deposit_products — new columns ───────────────────────────────────────────

ALTER TABLE deposit_products
    ADD COLUMN IF NOT EXISTS short_name                             VARCHAR(4)       UNIQUE,
    ADD COLUMN IF NOT EXISTS min_required_opening_balance           NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS interest_posting_period_type           VARCHAR(20)      NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN IF NOT EXISTS days_in_year_type                      VARCHAR(20)      NOT NULL DEFAULT 'ACTUAL',
    ADD COLUMN IF NOT EXISTS days_in_month_type                     VARCHAR(20)      NOT NULL DEFAULT 'ACTUAL',
    ADD COLUMN IF NOT EXISTS lockin_period_frequency                INTEGER,
    ADD COLUMN IF NOT EXISTS lockin_period_frequency_type           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS withdrawal_fee_for_transfers           BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS allow_overdraft                        BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS overdraft_limit                        NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS nominal_annual_interest_rate_overdraft NUMERIC(8,4),
    ADD COLUMN IF NOT EXISTS min_overdraft_for_interest_calculation NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS accounting_type                        VARCHAR(20)      NOT NULL DEFAULT 'NONE',

    -- GL account linkages
    ADD COLUMN IF NOT EXISTS savings_reference_account_id           UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS savings_control_account_id             UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS transfers_in_suspense_account_id       UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS interest_on_savings_account_id         UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS income_from_fees_account_id            UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS income_from_penalties_account_id       UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS write_off_account_id                   UUID REFERENCES gl_accounts(id),
    ADD COLUMN IF NOT EXISTS overdraft_portfolio_control_account_id UUID REFERENCES gl_accounts(id);

-- Backfill short_name for existing deposit product rows (deduplicate with row-number suffix)
WITH ranked AS (
    SELECT id,
           UPPER(SUBSTRING(REGEXP_REPLACE(name, '[^A-Za-z0-9]', '', 'g'), 1, 4)) AS base_code,
           ROW_NUMBER() OVER (
               PARTITION BY UPPER(SUBSTRING(REGEXP_REPLACE(name, '[^A-Za-z0-9]', '', 'g'), 1, 4))
               ORDER BY id
           ) AS rn
    FROM deposit_products
    WHERE short_name IS NULL
)
UPDATE deposit_products dp
SET short_name = CASE WHEN r.rn = 1 THEN r.base_code
                      ELSE SUBSTRING(r.base_code, 1, 3) || CAST(r.rn AS VARCHAR)
                 END
FROM ranked r
WHERE dp.id = r.id;

ALTER TABLE deposit_products
    ALTER COLUMN short_name SET NOT NULL;

-- ── Join tables — product charges ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS loan_product_charges (
    loan_product_id    UUID NOT NULL REFERENCES loan_products(id)       ON DELETE CASCADE,
    charge_definition_id UUID NOT NULL REFERENCES charge_definitions(id) ON DELETE CASCADE,
    PRIMARY KEY (loan_product_id, charge_definition_id)
);

CREATE TABLE IF NOT EXISTS deposit_product_charges (
    deposit_product_id   UUID NOT NULL REFERENCES deposit_products(id)    ON DELETE CASCADE,
    charge_definition_id UUID NOT NULL REFERENCES charge_definitions(id)  ON DELETE CASCADE,
    PRIMARY KEY (deposit_product_id, charge_definition_id)
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_loan_products_fund_id             ON loan_products(fund_id);
CREATE INDEX IF NOT EXISTS idx_loan_products_fund_source         ON loan_products(fund_source_account_id);
CREATE INDEX IF NOT EXISTS idx_loan_products_loan_portfolio      ON loan_products(loan_portfolio_account_id);
CREATE INDEX IF NOT EXISTS idx_deposit_products_savings_ref      ON deposit_products(savings_reference_account_id);
CREATE INDEX IF NOT EXISTS idx_deposit_products_savings_ctrl     ON deposit_products(savings_control_account_id);
